import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClientePrototipo {
    public static void main(String[] args) throws Exception {
        Socket socket = new Socket("127.0.0.1", 12345);
        
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        Scanner teclado = new Scanner(System.in);

        // Thread 2 do Cliente: Recebe dados da conexão em loop e imprime na tela
        new Thread(() -> {
            try {
                String mensagemServidor;
                while ((mensagemServidor = in.readLine()) != null) {
                    System.out.println(mensagemServidor);
                }
            } catch (Exception e) {
                System.out.println("Conexao fechada.");
            }
        }).start();

        // Thread 1 do Cliente (Main): Lê do teclado e envia pelo socket
        while (true) {
            String comando = teclado.nextLine();
            out.println(comando);
            
            if (comando.equals("Exit")) {
                break;
            }
        }
        
        socket.close();
        teclado.close();
    }
}