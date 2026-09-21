import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ClienteLoteria {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        try (Socket socket = new Socket(host, 12347);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner teclado = new Scanner(System.in)) {
            Thread leitor = new Thread(() -> {
                try {
                    String mensagem;
                    while ((mensagem = in.readLine()) != null) System.out.println(mensagem);
                } catch (IOException ignored) {
                }
            });
            leitor.setDaemon(true);
            leitor.start();

            while (teclado.hasNextLine()) {
                String linha = teclado.nextLine();
                out.println(linha);
                if (linha.equalsIgnoreCase(":quit") || linha.equalsIgnoreCase("Exit")) break;
            }
        } catch (IOException e) {
            System.err.println("Nao foi possivel conectar ao servidor: " + e.getMessage());
        }
    }
}
