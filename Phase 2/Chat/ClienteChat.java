import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ClienteChat {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";

        // Conecta ao servidor na porta 12346
        try (Socket socket = new Socket(host, 12346);
             // Cria leitor de mensagens do servidor e emissor de mensagens do cliente
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner teclado = new Scanner(System.in)) {

            // Exibe o que o servidor enviar
            Thread leitor = new Thread(() -> {
                try {
                    String mensagem;
                    while ((mensagem = in.readLine()) != null) System.out.println(mensagem);
                } catch (IOException ignored) {
                    // conexao fechada, so encerra a leitura
                }
            });
            // Daemon: a thread nao impede o programa de terminar quando o cliente sair
            leitor.setDaemon(true);
            leitor.start();

            // Envia o que for digitado para o servidor
            while (teclado.hasNextLine()) {
                String linha = teclado.nextLine();
                out.println(linha);
                if (linha.equalsIgnoreCase(":quit") || linha.equalsIgnoreCase("Exit")) break;
            }
        } catch (IOException e) {
            // Erro ao conectar
            System.err.println("Nao foi possivel conectar ao servidor: " + e.getMessage());
        }
    }
}
