import java.io.*;
import java.net.*;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

public class ServidorPrototipo {
    static volatile boolean monitorRodando = false; 

    public static void main(String[] args) throws Exception {
        ServerSocket server = new ServerSocket(12345);
        System.out.println("Servidor aguardando conexao na porta 12345...");
        
        Socket socket = server.accept();
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        out.println("<12:00>: CONECTADO!! Menu: CPU, memoria, Quit, Exit");

        String comando;
        while ((comando = in.readLine()) != null) {
            
            if (comando.startsWith("CPU-")) {
                int tempoSegundos = Integer.parseInt(comando.split("-")[1]);
                monitorRodando = true;
                
                new Thread(() -> {
                    try {
                        while (monitorRodando) {
                            double cpu = osBean.getCpuLoad() * 100;
                            out.println("MONITOR CPU: " + String.format("%.2f", cpu) + "%");
                            Thread.sleep(tempoSegundos * 1000L);
                        }
                        out.println("Monitor CPU encerrado.");
                    } catch (Exception e) { }
                }).start();
                
            } else if (comando.equals("Quit")) {
                monitorRodando = false; // Interrompe a thread acima
            } else if (comando.equals("Exit")) {
                break;
            }
        }
        
        System.out.println("Encerrando servidor...");
        socket.close();
        server.close();
    }
}