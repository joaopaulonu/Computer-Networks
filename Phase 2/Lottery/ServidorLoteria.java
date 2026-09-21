import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ServidorLoteria {
    private static final int PORTA = 12347;
    private static final AtomicInteger CLIENTES_ATIVOS = new AtomicInteger();

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Uso: java ServidorLoteria <max_clientes>");
            return;
        }
        int limite;
        try {
            limite = Integer.parseInt(args[0]);
            if (limite < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            System.err.println("O limite deve ser um inteiro maior que zero.");
            return;
        }

        try (ServerSocket server = new ServerSocket(PORTA)) {
            System.out.println("Servidor de loteria na porta " + PORTA + ". Limite: " + limite);
            while (true) {
                Socket socket = server.accept();
                if (CLIENTES_ATIVOS.incrementAndGet() > limite) {
                    CLIENTES_ATIVOS.decrementAndGet();
                    try (Socket recusado = socket;
                         PrintWriter out = new PrintWriter(recusado.getOutputStream(), true)) {
                        out.println("Limite de clientes atingido.");
                    }
                    continue;
                }
                new Thread(new ClienteHandler(socket)).start();
            }
        }
    }

    private static final class ClienteHandler implements Runnable {
        private final Socket socket;
        private final List<Integer> apostas = new ArrayList<>();
        private final Random random = new Random();
        private PrintWriter out;
        private int inicio = 0;
        private int fim = 100;
        private int quantidade = 5;
        private volatile boolean ativo = true;

        private ClienteHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (Socket cliente = socket;
                 BufferedReader in = new BufferedReader(new InputStreamReader(cliente.getInputStream()))) {
                out = new PrintWriter(cliente.getOutputStream(), true);
                enviar("<LOTERIA>: CONECTADO!! Padrao: 0 a 100, 5 numeros.");

                Thread sorteador = new Thread(this::sortearPeriodicamente, "sorteador-" + socket.getPort());
                sorteador.setDaemon(true);
                sorteador.start();

                String linha;
                while (ativo && (linha = in.readLine()) != null) processar(linha.trim());
            } catch (IOException e) {
                if (ativo) enviar("Conexao encerrada.");
            } finally {
                ativo = false;
                CLIENTES_ATIVOS.decrementAndGet();
            }
        }

        private void processar(String linha) {
            if (linha.equalsIgnoreCase(":quit") || linha.equalsIgnoreCase("Exit")) {
                ativo = false;
                return;
            }
            if (linha.startsWith(":")) {
                configurar(linha);
                return;
            }
            if (linha.isEmpty()) return;
            String[] valores = linha.split("\\s+");
            List<Integer> aposta = new ArrayList<>();
            try {
                for (String valor : valores) aposta.add(Integer.parseInt(valor));
                if (aposta.size() != quantidade) {
                    enviar("A aposta deve ter exatamente " + quantidade + " numeros.");
                    return;
                }
                for (int numero : aposta) {
                    if (numero < inicio || numero > fim) throw new NumberFormatException();
                }
                synchronized (apostas) {
                    apostas.addAll(aposta);
                }
                enviar("Aposta registrada: " + aposta);
            } catch (NumberFormatException e) {
                enviar("Aposta invalida. Use numeros entre " + inicio + " e " + fim + ".");
            }
        }

        private void configurar(String linha) {
            String[] partes = linha.split("\\s+");
            if (partes.length != 2) {
                enviar("Use :inicio N, :fim N ou :qtd N.");
                return;
            }
            try {
                int valor = Integer.parseInt(partes[1]);
                int novoInicio = inicio;
                int novoFim = fim;
                int novaQuantidade = quantidade;
                switch (partes[0].toLowerCase()) {
                    case ":inicio": novoInicio = valor; break;
                    case ":fim": novoFim = valor; break;
                    case ":qtd": novaQuantidade = valor; break;
                    default: enviar("Comando desconhecido."); return;
                }
                if (novoInicio >= novoFim || novaQuantidade < 1
                        || novaQuantidade > novoFim - novoInicio + 1) {
                    enviar("Configuracao invalida: intervalo ou quantidade incompatível.");
                } else {
                    inicio = novoInicio;
                    fim = novoFim;
                    quantidade = novaQuantidade;
                    enviar("Configuracao atualizada.");
                }
            } catch (NumberFormatException e) {
                enviar("O valor deve ser inteiro.");
            }
        }

        private void sortearPeriodicamente() {
            while (ativo) {
                try {
                    Thread.sleep(60000L);
                    if (!ativo) break;
                    List<Integer> sorteados = sortear();
                    List<Integer> minhasApostas;
                    synchronized (apostas) {
                        minhasApostas = new ArrayList<>(apostas);
                        apostas.clear();
                    }
                    Set<Integer> acertos = new HashSet<>(sorteados);
                    acertos.retainAll(minhasApostas);
                    enviar("Sorteio: " + sorteados + " | Acertos: " + acertos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private List<Integer> sortear() {
            List<Integer> numeros = new ArrayList<>();
            for (int numero = inicio; numero <= fim; numero++) numeros.add(numero);
            Collections.shuffle(numeros, random);
            return new ArrayList<>(numeros.subList(0, quantidade));
        }

        private synchronized void enviar(String mensagem) {
            if (out != null) out.println(mensagem);
        }
    }
}
