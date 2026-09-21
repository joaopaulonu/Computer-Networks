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

        out.println("<12:00>: CONECTADO!! Menu: CPU-segundos, MEM-segundos, Quit, Exit");

        String comando;
        while ((comando = in.readLine()) != null) {
            
            String[] partesComando = comando.split("-", -1);

            if (partesComando.length == 2
                    && (partesComando[0].equals("CPU") || partesComando[0].equals("MEM"))) {
                try {
                    int tempoSegundos = Integer.parseInt(partesComando[1]);
                    if (tempoSegundos <= 0) {
                        out.println("O intervalo deve ser maior que zero.");
                        continue;
                    }

                    monitorRodando = false;
                    boolean monitorarCpu = partesComando[0].equals("CPU");
                    monitorRodando = true;

                    new Thread(() -> {
                        try {
                            while (monitorRodando) {
                                if (monitorarCpu) {
                                    double cpu = osBean.getCpuLoad() * 100;
                                    out.println("MONITOR CPU: " + String.format("%.2f", cpu) + "%");
                                } else {
                                    long totalMemoria = osBean.getTotalMemorySize();
                                    long memoriaLivre = osBean.getFreeMemorySize();
                                    double memoriaUsada = (double) (totalMemoria - memoriaLivre) / totalMemoria * 100;
                                    out.println("MONITOR MEMORIA: " + String.format("%.2f", memoriaUsada) + "%");
                                }
                                Thread.sleep(tempoSegundos * 1000L);
                            }
                            out.println("Monitor encerrado.");
                        } catch (Exception e) {
                            out.println("Erro no monitoramento.");
                        }
                    }).start();
                } catch (NumberFormatException e) {
                    out.println("Intervalo invalido. Use CPU-segundos ou MEM-segundos.");
                }
            } else if (comando.equals("Quit")) {
                monitorRodando = false;
            } else if (comando.equals("Exit")) {
                break;
            } else {
                out.println("Comando invalido. Use CPU-segundos, MEM-segundos, Quit ou Exit.");
            }
        }
        
        System.out.println("Encerrando servidor...");
        socket.close();
        server.close();
    }
}