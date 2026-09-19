import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

public class ServidorPrototipo {

    static final int PORTA = 12345;
    static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    // [CONTROLO DE LIMITE] Contador thread-safe de clientes atualmente ligados.
    static final AtomicInteger clientesAtivos = new AtomicInteger(0);

    // O OperatingSystemMXBean pode ser partilhado entre threads (apenas leitura de métricas).
    static final OperatingSystemMXBean osBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    public static void main(String[] args) throws Exception {
        // [CONTROLO DE LIMITE] Limite máximo de clientes vem de args[0].
        if (args.length < 1) {
            System.err.println("Uso: java ServidorPrototipo <max_clientes>");
            return;
        }
        final int limiteClientes;
        try {
            limiteClientes = Integer.parseInt(args[0]);
            if (limiteClientes < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            System.err.println("Erro: <max_clientes> deve ser um inteiro >= 1.");
            return;
        }

        try (ServerSocket server = new ServerSocket(PORTA)) {
            System.out.println("Servidor aguardando conexoes na porta " + PORTA
                    + " (limite: " + limiteClientes + " clientes)...");

            // [WORKER THREADS] A thread principal só faz accept() e delega; nunca atende comandos.
            while (true) {
                Socket socket = server.accept();

                // [CONTROLO DE LIMITE] Reserva atómica de vaga. Só a thread principal incrementa,
                // por isso nunca há mais do que 'limite' workers ativos; os workers apenas decrementam.
                if (clientesAtivos.incrementAndGet() > limiteClientes) {
                    clientesAtivos.decrementAndGet();   // devolve a vaga que não foi concedida
                    rejeitarCliente(socket, limiteClientes);
                    continue;                           // sem Worker Thread; volta ao accept()
                }

                Thread worker = new Thread(new ClienteHandler(socket));
                worker.start();                         // volta imediatamente ao accept()
            }
        }
    }

    // [REJEIÇÃO ATIVA] Aceita o socket só para informar e fechar de imediato.
    private static void rejeitarCliente(Socket socket, int limite) {
        try (Socket s = socket;
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.println("<" + LocalTime.now().format(HORA) + ">: RECUSADO! Limite de "
                    + limite + " clientes excedido. Tente mais tarde.");
        } catch (IOException e) {
            // Cliente já desapareceu; nada a fazer.
        }
        System.out.println("Ligacao recusada (limite atingido): " + socket.getRemoteSocketAddress());
    }

    // ------------------------------------------------------------------
    // [WORKER THREAD] Uma instância por cliente: socket e estado são próprios.
    // ------------------------------------------------------------------
    static class ClienteHandler implements Runnable {
        private final Socket socket;
        private final String id;
        private PrintWriter out;

        // [ISOLAMENTO DE ESTADO] Antes era static/global; agora é campo de instância,
        // logo cada cliente tem a sua própria flag e o seu próprio monitor.
        private volatile boolean monitorRodando = false;
        private Thread monitorThread;   // só acedido pela worker thread do cliente

        ClienteHandler(Socket socket) {
            this.socket = socket;
            this.id = String.valueOf(socket.getRemoteSocketAddress());
        }

        @Override
        public void run() {
            System.out.println("Cliente ligado: " + id + " | ativos: " + clientesAtivos.get());
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("<" + LocalTime.now().format(HORA)
                        + ">: CONECTADO!! Menu: CPU-<segundos>, memoria, Quit, Exit");

                String comando;
                while ((comando = in.readLine()) != null) {
                    comando = comando.trim();

                    if (comando.startsWith("CPU-")) {
                        iniciarMonitorCpu(comando);
                    } else if (comando.equalsIgnoreCase("memoria")) {
                        enviarMemoria();
                    } else if (comando.equals("Quit")) {
                        if (monitorRodando) {
                            pararMonitor();
                        } else {
                            out.println("Nenhum monitor em execucao.");
                        }
                    } else if (comando.equals("Exit")) {
                        break;
                    } else {
                        out.println("Comando desconhecido. Menu: CPU-<segundos>, memoria, Quit, Exit");
                    }
                }
            } catch (IOException e) {
                System.out.println("Erro de I/O com " + id + ": " + e.getMessage());
            } finally {
                // [DESCONEXÃO LIMPA] Corre sempre (Exit, queda de rede ou erro):
                pararMonitor();                                   // 1) para monitores deste cliente
                try { socket.close(); } catch (IOException ignored) { } // 2) fecha a TCP
                int restantes = clientesAtivos.decrementAndGet();  // 3) liberta a vaga (uma só vez)
                System.out.println("Cliente desligado: " + id + " | ativos: " + restantes);
            }
        }

        private void iniciarMonitorCpu(String comando) {
            if (monitorRodando) {
                out.println("Ja existe um monitor CPU ativo. Use Quit para o parar.");
                return;
            }
            final int tempoSegundos;
            try {
                tempoSegundos = Integer.parseInt(comando.split("-", 2)[1].trim());
                if (tempoSegundos < 1) throw new NumberFormatException();
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                out.println("Formato invalido. Use CPU-<segundos> com segundos >= 1 (ex.: CPU-5).");
                return;
            }

            monitorRodando = true;
            monitorThread = new Thread(() -> {
                try {
                    while (monitorRodando) {
                        double cpu = osBean.getCpuLoad() * 100;
                        String valor = cpu < 0 || Double.isNaN(cpu) ? "N/D" : String.format("%.2f%%", cpu);
                        out.println("MONITOR CPU: " + valor);
                        Thread.sleep(tempoSegundos * 1000L);
                    }
                } catch (InterruptedException e) {
                    // interrompida por pararMonitor(): sai do ciclo imediatamente
                }
                out.println("Monitor CPU encerrado.");
            });
            monitorThread.setDaemon(true);
            monitorThread.start();
        }

        // Para o monitor DESTE cliente (não afeta os restantes).
        private void pararMonitor() {
            monitorRodando = false;
            if (monitorThread != null) {
                monitorThread.interrupt();   // acorda o sleep para terminar de imediato
                monitorThread = null;
            }
        }

        // [BÓNUS FASE 1] Comando 'memoria'.
        private void enviarMemoria() {
            long total = osBean.getTotalPhysicalMemorySize();
            long livre = osBean.getFreePhysicalMemorySize();
            long usada = total - livre;
            double pct = total > 0 ? (usada * 100.0) / total : 0;
            out.println(String.format("MEMORIA: usada %d MB / total %d MB (livre %d MB) - %.2f%% em uso",
                    usada / (1024 * 1024), total / (1024 * 1024), livre / (1024 * 1024), pct));
        }
    }
}