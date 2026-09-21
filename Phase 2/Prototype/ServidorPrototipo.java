import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

public class ServidorPrototipo {

    static final int PORTA = 12345;
    static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    static final AtomicInteger clientesAtivos = new AtomicInteger(0);

    static final OperatingSystemMXBean osBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    public static void main(String[] args) throws Exception {
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

            while (true) {
                Socket socket = server.accept();

                if (clientesAtivos.incrementAndGet() > limiteClientes) {
                    clientesAtivos.decrementAndGet();
                    rejeitarCliente(socket, limiteClientes);
                    continue;
                }

                Thread worker = new Thread(new ClienteHandler(socket));
                worker.start();
            }
        }
    }

    private static void rejeitarCliente(Socket socket, int limite) {
        try (Socket s = socket;
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.println("<" + LocalTime.now().format(HORA) + ">: RECUSADO! Limite de "
                    + limite + " clientes excedido. Tente mais tarde.");
        } catch (IOException e) {
        }
        System.out.println("Ligacao recusada (limite atingido): " + socket.getRemoteSocketAddress());
    }

    static class ClienteHandler implements Runnable {
        private final Socket socket;
        private final String id;
        private PrintWriter out;

        private final Map<String, MonitorTask> monitores = new ConcurrentHashMap<>();

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

                    if (comando.regionMatches(true, 0, "CPU-", 0, 4)
                            || comando.regionMatches(true, 0, "memoria-", 0, 8)) {
                        iniciarMonitorCpu(comando);
                    } else if (comando.equalsIgnoreCase("memoria")) {
                        enviarMemoria();
                    } else if (comando.equalsIgnoreCase("Quit")) {
                        if (!monitores.isEmpty()) {
                            pararMonitor();
                        } else {
                            out.println("Nenhum monitor em execucao.");
                        }
                    } else if (comando.equalsIgnoreCase("Exit")) {
                        break;
                    } else {
                        out.println("Comando desconhecido. Menu: CPU-<segundos>, memoria, Quit, Exit");
                    }
                }
            } catch (IOException e) {
                System.out.println("Erro de I/O com " + id + ": " + e.getMessage());
            } finally {
                pararMonitor();
                try { socket.close(); } catch (IOException ignored) { }
                int restantes = clientesAtivos.decrementAndGet();
                System.out.println("Cliente desligado: " + id + " | ativos: " + restantes);
            }
        }

        private void iniciarMonitorCpu(String comando) {
            String[] partes = comando.split("-", 2);
            String tipo = partes[0].trim().toLowerCase();
            final int tempoSegundos;
            try {
                tempoSegundos = Integer.parseInt(partes[1].trim());
                if (tempoSegundos < 1) throw new NumberFormatException();
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                out.println("Formato invalido. Use CPU-<segundos> ou memoria-<segundos>.");
                return;
            }

            MonitorTask anterior = monitores.remove(tipo);
            if (anterior != null) anterior.parar();

            MonitorTask tarefa = new MonitorTask(tipo, tempoSegundos);
            monitores.put(tipo, tarefa);
            Thread thread = new Thread(tarefa, "monitor-" + tipo + "-" + id);
            tarefa.thread = thread;
            thread.setDaemon(true);
            thread.start();
            out.println("Monitor " + tipo + " iniciado a cada " + tempoSegundos + "s.");
        }

        private void pararMonitor() {
            for (MonitorTask tarefa : monitores.values()) tarefa.parar();
            monitores.clear();
        }

        private final class MonitorTask implements Runnable {
            private final String tipo;
            private final int intervalo;
            private volatile boolean executando = true;
            private Thread thread;

            private MonitorTask(String tipo, int intervalo) {
                this.tipo = tipo;
                this.intervalo = intervalo;
            }

            private void parar() {
                executando = false;
                if (thread != null) thread.interrupt();
            }

            @Override
            public void run() {
                try {
                    while (executando) {
                        if (tipo.equals("cpu")) {
                            double cpu = osBean.getCpuLoad() * 100;
                            String valor = cpu < 0 || Double.isNaN(cpu)
                                    ? "N/D" : String.format("%.2f%%", cpu);
                            out.println("MONITOR CPU: " + valor);
                        } else {
                            enviarMemoria();
                        }
                        Thread.sleep(intervalo * 1000L);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    out.println("Monitor " + tipo + " encerrado.");
                }
            }
        }

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