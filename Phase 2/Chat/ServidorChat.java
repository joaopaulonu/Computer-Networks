import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ServidorChat {
    private static final int PORTA = 12346;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final AtomicInteger CLIENTES_ATIVOS = new AtomicInteger();
    private static final Map<ClienteHandler, String> CLIENTES = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Uso: java ServidorChat <max_clientes>");
            return;
        }

        int limite;
        try {
            limite = Integer.parseInt(args[0]);
            if (limite < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            System.err.println("O limite deve ser um inteiro maior que zero.");
            return;
        }

        try (ServerSocket server = new ServerSocket(PORTA)) {
            System.out.println("Servidor de chat na porta " + PORTA + ". Limite: " + limite);
            while (true) {
                Socket socket = server.accept();
                if (CLIENTES_ATIVOS.incrementAndGet() > limite) {
                    CLIENTES_ATIVOS.decrementAndGet();
                    try (Socket recusado = socket;
                         PrintWriter out = new PrintWriter(recusado.getOutputStream(), true)) {
                        out.println("Limite de clientes atingido.");
                    }
                    continue;
                }
                new Thread(new ClienteHandler(socket)).start();
            }
        }
    }

    private static String horario() {
        return LocalTime.now().format(HORA);
    }

    private static void enviarTodos(String mensagem) {
        for (ClienteHandler cliente : CLIENTES.keySet()) cliente.enviar(mensagem);
    }

    private static final class ClienteHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private String nome;
        private volatile boolean ativo = true;

        private ClienteHandler(Socket socket) {
            this.socket = socket;
            this.nome = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }

        @Override
        public void run() {
            try (Socket cliente = socket;
                 BufferedReader in = new BufferedReader(new InputStreamReader(cliente.getInputStream()))) {
                out = new PrintWriter(cliente.getOutputStream(), true);
                CLIENTES.put(this, nome);
                enviar(horario() + ": CONECTADO!! Use :nome <NOME> ou :quit.");

                Thread relogio = new Thread(() -> {
                    while (ativo) {
                        try {
                            Thread.sleep(60000L);
                            if (ativo) enviar("HORARIO: " + horario());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }, "relogio-chat-" + nome);
                relogio.setDaemon(true);
                relogio.start();

                String linha;
                while (ativo && (linha = in.readLine()) != null) processar(linha.trim());
            } catch (IOException e) {
                if (ativo) System.out.println("Cliente desconectado: " + nome);
            } finally {
                ativo = false;
                CLIENTES.remove(this);
                CLIENTES_ATIVOS.decrementAndGet();
            }
        }

        private void processar(String linha) {
            if (linha.equalsIgnoreCase(":quit") || linha.equalsIgnoreCase("Exit")) {
                ativo = false;
                return;
            }
            if (linha.regionMatches(true, 0, ":nome ", 0, 6)) {
                String novoNome = linha.substring(6).trim();
                if (!novoNome.isEmpty()) {
                    nome = novoNome;
                    CLIENTES.put(this, nome);
                    enviar("Nome alterado para: " + nome);
                } else enviar("Uso: :nome <NOME>");
                return;
            }
            if (!linha.isEmpty()) {
                enviar("Voce digitou: " + linha);
                enviarTodos(nome + " (" + horario() + "): " + linha);
            }
        }

        private synchronized void enviar(String mensagem) {
            if (out != null) out.println(mensagem);
        }
    }
}
