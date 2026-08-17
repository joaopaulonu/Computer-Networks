import com.sun.management.OperatingSystemMXBean;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor de Monitoramento Remoto de Sistema.
 * Gerencia sockets TCP e spowna threads de monitoramento sob demanda.
 */
public class Servidor {
    private static final int PORTA = 12345;

    public static void main(String[] args) {
        System.out.println("[SERVER] Iniciando Servidor de Monitoramento...");
        try (ServerSocket serverSocket = new ServerSocket(PORTA)) {
            System.out.println("[SERVER] Aguardando conexões na porta " + PORTA + "...");
            
            // Loop para aceitar cliente (Fase 1: processa a conexão ativa)
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SERVER] Cliente conectado de: " + clientSocket.getRemoteSocketAddress());
                
                // Thread 1 do Servidor: Gerencia a sessão com o cliente
                new Thread(new GerenciadorCliente(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("[SERVER-ERRO] Falha crítica no servidor: " + e.getMessage());
        }
    }

    // =========================================================================
    // CLASSES INTERNAS (Mapeamento de Arquitetura em único arquivo)
    // =========================================================================

    /**
     * Thread 1 do Servidor: Escuta e interpreta os comandos vindo da rede.
     */
    private static class GerenciadorCliente implements Runnable {
        private final Socket socket;
        // Memória Compartilhada Thread-Safe para controlar as threads de monitoramento ativas[cite: 1]
        private final Map<String, MonitorTask> monitoresAtivos = new ConcurrentHashMap<>();

        public GerenciadorCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
            ) {
                // Envio da Mensagem MSG1 exigida pela especificação[cite: 1]
                String msgBoasVindas = String.format("<%s>: CONECTADO!! Menu: CPU-<seg>, memoria-<seg>, Quit, Exit", 
                        FormatadorData.obterHorarioAtual());
                out.println(msgBoasVindas);

                String comando;
                // Loop de leitura do socket[cite: 1]
                while ((comando = in.readLine()) != null) {
                    comando = comando.trim();

                    if (comando.startsWith("CPU-") || comando.startsWith("memoria-")) {
                        iniciarMonitor(comando, out);
                    } else if (comando.equalsIgnoreCase("Quit")) {
                        pararTodosMonitores(out);
                    } else if (comando.equalsIgnoreCase("Exit")) {
                        out.println("[SERVER] Encerrando conexão por solicitação de Exit.");
                        break;
                    } else {
                        out.println("[SERVER-ERRO] Comando inválido! Use: CPU-<seg>, memoria-<seg>, Quit ou Exit.");
                    }
                }
            } catch (IOException e) {
                System.err.println("[SERVER] Cliente desconectado abruptamente.");
            } finally {
                pararTodosMonitores(null);
                fecharSocket();
            }
        }

        private void iniciarMonitor(String comando, PrintWriter out) {
            String[] partes = comando.split("-");
            if (partes.length != 2) {
                out.println("[SERVER-ERRO] Sintaxe incorreta. Exemplo esperado: CPU-5 ou memoria-2");
                return;
            }

            String tipo = partes[0];
            int intervalo;

            try {
                intervalo = Integer.parseInt(partes[1]);
                if (intervalo <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                out.println("[SERVER-ERRO] O tempo de atualização deve ser um número inteiro positivo.");
                return;
            }

            // Se o monitor já estiver rodando, encerra a versão anterior antes de abrir nova
            if (monitoresAtivos.containsKey(tipo)) {
                monitoresAtivos.get(tipo).parar();
            }

            // Instancia e inicia a thread secundária de monitoramento[cite: 1]
            MonitorTask task = new MonitorTask(tipo, intervalo, out);
            monitoresAtivos.put(tipo, task);
            new Thread(task).start();
            
            out.println("[SERVER] Monitor de " + tipo.toUpperCase() + " iniciado a cada " + intervalo + "s.");
        }

        private void pararTodosMonitores(PrintWriter out) {
            if (monitoresAtivos.isEmpty()) {
                if (out != null) out.println("[SERVER] Nenhum monitor ativo no momento.");
                return;
            }

            for (Map.Entry<String, MonitorTask> entry : monitoresAtivos.entrySet()) {
                entry.getValue().parar();
            }
            monitoresAtivos.clear();
            if (out != null) out.println("[SERVER] Todos os monitores foram interrompidos.");
        }

        private void fecharSocket() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println("[SERVER] Erro ao fechar socket: " + e.getMessage());
            }
        }
    }

    /**
     * Threads Secundárias: Tarefa de coleta de métricas em loop[cite: 1].
     */
    private static class MonitorTask implements Runnable {
        private final String tipo;
        private final int intervaloSegundos;
        private final PrintWriter out;
        // Flag de memória compartilhada para parar a thread de forma limpa[cite: 1]
        private volatile boolean executando = true;
        private final OperatingSystemMXBean osBean;

        public MonitorTask(String tipo, int intervaloSegundos, PrintWriter out) {
            this.tipo = tipo;
            this.intervaloSegundos = intervaloSegundos;
            this.out = out;
            this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        }

        public void parar() {
            this.executando = false;
        }

        @Override
        public void run() {
            while (executando) {
                try {
                    String dados = coletarMetrica();
                    out.println(String.format("[%s] %s", FormatadorData.obterHorarioAtual(), dados));
                    Thread.sleep(intervaloSegundos * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    out.println("[MONITOR-ERRO] Falha ao coletar métricas de " + tipo);
                    break;
                }
            }
        }

        private String coletarMetrica() {
            if ("CPU".equalsIgnoreCase(tipo)) {
                double cpuLoad = osBean.getCpuLoad() * 100;
                if (cpuLoad < 0) cpuLoad = 0.0; // Tratamento para primeiras leituras do sistema
                return String.format("MÉTRICA CPU: Uso = %.2f%%", cpuLoad);
            } else if ("memoria".equalsIgnoreCase(tipo)) {
                long totalMem = osBean.getTotalMemorySize();
                long freeMem = osBean.getFreeMemorySize();
                long usedMem = totalMem - freeMem;
                double usoPorcentagem = ((double) usedMem / totalMem) * 100;
                
                return String.format("MÉTRICA MEMÓRIA: Uso = %.2f%% (%d MB / %d MB)", 
                        usoPorcentagem, usedMem / (1024 * 1024), totalMem / (1024 * 1024));
            }
            return "Métrica desconhecida.";
        }
    }

    /**
     * Utilitário para formatação da data/hora nos padrões exigidos[cite: 1].
     */
    private static class FormatadorData {
        private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

        public static String obterHorarioAtual() {
            return LocalDateTime.now().format(FORMATO_HORA);
        }
    }
}