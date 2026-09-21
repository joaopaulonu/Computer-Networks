import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Cliente {
    private static volatile boolean conexaoEncerrada = false;
    private static final int PORTA_SERVIDOR = 12345;

    public static void main(String[] args) {
        String ipServidor = args.length > 0 ? args[0] : "127.0.0.1";
        System.out.println("[CLIENTE] Tentando conectar ao servidor " + ipServidor + ":" + PORTA_SERVIDOR + "...");

        try {
            Socket socket = new Socket(ipServidor, PORTA_SERVIDOR);
            
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            Thread threadOuvinte = new Thread(() -> {
                try {
                    String mensagemServidor;
                    while ((mensagemServidor = in.readLine()) != null) {
                        System.out.println(mensagemServidor);
                    }
                } catch (IOException ignored) {
                } finally {
                    if (!conexaoEncerrada) {
                        System.out.println("[CLIENTE] Conexao encerrada pelo servidor.");
                        System.exit(0);
                    }
                }
            });
            threadOuvinte.setDaemon(true);
            threadOuvinte.start();

            Thread threadTeclado = new Thread(() -> {
                try (Scanner teclado = new Scanner(System.in)) {
                    while (teclado.hasNextLine()) {
                        String comando = teclado.nextLine();
                        out.println(comando);

                        if (comando.equalsIgnoreCase("Exit")) {
                            conexaoEncerrada = true;
                            System.out.println("[CLIENTE] Encerrando aplicação cliente...");
                            break;
                        }
                    }
                }
            });
            threadTeclado.start();
            try {
                threadTeclado.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            socket.close();
            try {
                threadOuvinte.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        } catch (IOException e) {
            System.err.println("[CLIENTE-ERRO] Não foi possível conectar ao servidor. Certifique-se de que o Servidor.java está rodando!");
        }
    }

}