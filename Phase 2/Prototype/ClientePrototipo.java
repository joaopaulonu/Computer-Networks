import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClientePrototipo {

    // Distingue "saí eu (Exit)" de "o servidor fechou a ligação" (ex.: limite excedido).
    static volatile boolean saidaVoluntaria = false;

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        Socket socket = new Socket(host, 12345);

        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        Scanner teclado = new Scanner(System.in);

        Thread leitor = new Thread(() -> {
            try {
                String mensagemServidor;
                while ((mensagemServidor = in.readLine()) != null) {
                    System.out.println(mensagemServidor);
                }
            } catch (Exception e) {
                if (!saidaVoluntaria) System.out.println("Conexao fechada.");
            }
            // ADAPTAÇÃO FASE 2: se o servidor fechou a ligação (recusa por lotação ou queda),
            // termina o programa; senão a thread principal ficaria bloqueada no teclado.
            if (!saidaVoluntaria) {
                System.out.println("Ligacao terminada pelo servidor.");
                System.exit(0);
            }
        });
        leitor.setDaemon(true);
        leitor.start();

        while (teclado.hasNextLine()) {
            String comando = teclado.nextLine();
            out.println(comando);

            if (comando.trim().equals("Exit")) {
                saidaVoluntaria = true;
                break;
            }
        }

        socket.close();
        teclado.close();
    }
}