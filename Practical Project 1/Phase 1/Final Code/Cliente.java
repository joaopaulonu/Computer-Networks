import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * Cliente para Monitoramento Remoto de Sistema.
 * Conecta ao servidor e gerencia simultaneamente a leitura de teclado e exibições na tela[cite: 1].
 */
public class Cliente {
    private static final String IP_SERVIDOR = "127.0.0.1";
    private static final int PORTA_SERVIDOR = 12345;

    public static void main(String[] args) {
        System.out.println("[CLIENTE] Tentando conectar ao servidor " + IP_SERVIDOR + ":" + PORTA_SERVIDOR + "...");

        try {
            Socket socket = new Socket(IP_SERVIDOR, PORTA_SERVIDOR);
            
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Thread 2 do Cliente: Recebe dados do servidor em loop e imprime na tela[cite: 1]
            Thread threadOuvinte = new Thread(new OuvinteServidor(in));
            threadOuvinte.start();

            // Thread 1 do Cliente (Main): Lê comandos via teclado e envia pelo Socket[cite: 1]
            Scanner teclado = new Scanner(System.in);
            while (true) {
                String comando = teclado.nextLine();
                out.println(comando);

                if (comando.equalsIgnoreCase("Exit")) {
                    System.out.println("[CLIENTE] Encerrando aplicação cliente...");
                    break;
                }
            }

            // Encerramento adequado dos recursos
            socket.close();
            teclado.close();
            System.exit(0);

        } catch (IOException e) {
            System.err.println("[CLIENTE-ERRO] Não foi possível conectar ao servidor. Certifique-se de que o Servidor.java está rodando!");
        }
    }

    // =========================================================================
    // CLASSES INTERNAS
    // =========================================================================

    /**
     * Thread 2 do Cliente: Loop infinito de escuta da rede[cite: 1].
     */
    private static class OuvinteServidor implements Runnable {
        private final BufferedReader in;

        public OuvinteServidor(BufferedReader in) {
            this.in = in;
        }

        @Override
        public void run() {
            try {
                String mensagemServidor;
                // Loop de recepção e impressão contínua[cite: 1]
                while ((mensagemServidor = in.readLine()) != null) {
                    System.out.println(mensagemServidor);
                }
            } catch (IOException e) {
                System.out.println("[CLIENTE] Conexão com o servidor foi encerrada.");
            }
        }
    }
}