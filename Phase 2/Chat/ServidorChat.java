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
    // Porta onde o servidor escuta
    private static final int PORTA = 12346;
    // Formato do horario
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    // Conta quantos clientes estao conectados
    private static final AtomicInteger CLIENTES_ATIVOS = new AtomicInteger();
    // Guarda os clientes conectados e seus nomes
    private static final Map<ClienteHandler, String> CLIENTES = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        // O servidor precisa receber o limite de clientes como argumento
        if (args.length != 1) {
            System.err.println("Uso: java ServidorChat <max_clientes>");
            return;
        }

        // Converte o argumento para inteiro e valida
        int limite;
        try {
            limite = Integer.parseInt(args[0]);
            if (limite < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            System.err.println("O limite deve ser um inteiro maior que zero.");
            return;
        }

        // Inicia o servidor escutando a porta 12346
        try (ServerSocket server = new ServerSocket(PORTA)) {
            System.out.println("Servidor de chat na porta " + PORTA + ". Limite: " + limite);

            // Fica aceitando clientes para sempre
            while (true) {
                // Aguarda a conexao de um cliente
                Socket socket = server.accept();

                // Ve se ainda tem vaga, se passou do limite recusa o cliente
                if (CLIENTES_ATIVOS.incrementAndGet() > limite) {
                    CLIENTES_ATIVOS.decrementAndGet();
                    try (Socket recusado = socket;
                         PrintWriter out = new PrintWriter(recusado.getOutputStream(), true)) {
                        out.println("Limite de clientes atingido.");
                    }
                    continue;
                }

                // cada cliente aceito ganha sua propria thread
                new Thread(new ClienteHandler(socket)).start();
            }
        }
    }

    // Retorna a hora atual
    private static String horario() {
        return LocalTime.now().format(HORA);
    }

    // Envia a mensagem para todos os clientes
    private static void enviarTodos(String mensagem) {
        for (ClienteHandler cliente : CLIENTES.keySet()) cliente.enviar(mensagem);
    }

    // Classe que cuida de um cliente
    private static final class ClienteHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private String nome;
        // indica se o cliente ainda esta conectado
        private volatile boolean ativo = true;

        private ClienteHandler(Socket socket) {
            this.socket = socket;
            // nome inicial e o IP:porta do cliente
            this.nome = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }

        @Override
        public void run() {
            // Cria o leitor de mensagens do cliente
            try (Socket cliente = socket;
                 BufferedReader in = new BufferedReader(new InputStreamReader(cliente.getInputStream()))) {
                // Cria o emissor de mensagens para o cliente
                out = new PrintWriter(cliente.getOutputStream(), true);
                CLIENTES.put(this, nome);

                //Instrucao
                enviar(horario() + ": CONECTADO!! Use :nome <NOME> ou :quit.");

                // Inicia a thread que envia o horario a cada 60 segundos
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

                // Le e processa o que o cliente enviar ate ele sair
                String linha;
                while (ativo && (linha = in.readLine()) != null) processar(linha.trim());
            } catch (IOException e) {
                if (ativo) System.out.println("Cliente desconectado: " + nome);
            } finally {
                // Remove o cliente e libera a vaga
                ativo = false;
                CLIENTES.remove(this);
                CLIENTES_ATIVOS.decrementAndGet();
            }
        }

        // Decide o que fazer com a linha recebida: sair, trocar nome ou enviar mensagem
        private void processar(String linha) {
            // :quit ou Exit desconecta o cliente
            if (linha.equalsIgnoreCase(":quit") || linha.equalsIgnoreCase("Exit")) {
                ativo = false;
                return;
            }
            // comando :nome NOVO_NOME
            if (linha.regionMatches(true, 0, ":nome ", 0, 6)) {
                String novoNome = linha.substring(6).trim();
                if (!novoNome.isEmpty()) {
                    nome = novoNome;
                    CLIENTES.put(this, nome);
                    enviar("Nome alterado para: " + nome);
                } else enviar("Uso: :nome <NOME>");
                return;
            }
            // Se nao for comando, envia a mensagem para todos
            if (!linha.isEmpty()) {
                enviar("Voce digitou: " + linha);
                enviarTodos(nome + " (" + horario() + "): " + linha);
            }
        }

        // Envia mensagem ao cliente
        private synchronized void enviar(String mensagem) {
            if (out != null) out.println(mensagem);
        }
    }
}
