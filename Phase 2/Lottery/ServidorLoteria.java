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
    // Porta onde o servidor escuta
    private static final int PORTA = 12347;
    // Conta quantos clientes estao conectados
    private static final AtomicInteger CLIENTES_ATIVOS = new AtomicInteger();

    public static void main(String[] args) throws IOException {
        // O servidor precisa receber o limite de clientes como argumento
        if (args.length != 1) {
            System.err.println("Uso: java ServidorLoteria <max_clientes>");
            return;
        }

        // Converte o argumento para inteiro e valida
        int limite;
        try {
            limite = Integer.parseInt(args[0]);
            if (limite < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            System.err.println("O limite deve ser um inteiro maior que zero.");
            return;
        }

        // Inicia o servidor escutando a porta 12347
        try (ServerSocket server = new ServerSocket(PORTA)) {
            System.out.println("Servidor de loteria na porta " + PORTA + ". Limite: " + limite);

            // Fica aceitando clientes para sempre
            while (true) {
                // Aguarda a conexao de um cliente
                Socket socket = server.accept();

                // Ve se ainda tem vaga, se passou do limite recusa o cliente
                if (CLIENTES_ATIVOS.incrementAndGet() > limite) {
                    CLIENTES_ATIVOS.decrementAndGet();
                    try (Socket recusado = socket;
                         PrintWriter out = new PrintWriter(recusado.getOutputStream(), true)) {
                        out.println("Limite de clientes atingido.");
                    }
                    continue;
                }

                // cada cliente aceito ganha sua propria thread
                new Thread(new ClienteHandler(socket)).start();
            }
        }
    }

    // Classe que cuida de um cliente
    private static final class ClienteHandler implements Runnable {
        private final Socket socket;
        // numeros apostados pelo cliente
        private final List<Integer> apostas = new ArrayList<>();
        private final Random random = new Random();
        private PrintWriter out;
        // configuracao padrao: numeros de 0 a 100, 5 por aposta
        private int inicio = 0;
        private int fim = 100;
        private int quantidade = 5;
        // indica se o cliente ainda esta conectado
        private volatile boolean ativo = true;

        private ClienteHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            // Cria o leitor de mensagens do cliente
            try (Socket cliente = socket;
                 BufferedReader in = new BufferedReader(new InputStreamReader(cliente.getInputStream()))) {
                // Cria o emissor de mensagens para o cliente
                out = new PrintWriter(cliente.getOutputStream(), true);

                //instrucao
                enviar("<LOTERIA>: CONECTADO!! Padrao: 0 a 100, 5 numeros.");

                // Inicia a thread que faz o sorteio a cada 60 segundos
                Thread sorteador = new Thread(this::sortearPeriodicamente, "sorteador-" + socket.getPort());
                sorteador.setDaemon(true);
                sorteador.start();

                // Le e processa o que o cliente enviar ate ele sair
                String linha;
                while (ativo && (linha = in.readLine()) != null) processar(linha.trim());
            } catch (IOException e) {
                if (ativo) enviar("Conexao encerrada.");
            } finally {
                // libera a vaga e para o sorteador
                ativo = false;
                CLIENTES_ATIVOS.decrementAndGet();
            }
        }

        // Decide o que fazer com a linha recebida: sair, configurar ou apostar
        private void processar(String linha) {
            // :quit ou Exit desconecta o cliente
            if (linha.equalsIgnoreCase(":quit") || linha.equalsIgnoreCase("Exit")) {
                ativo = false;
                return;
            }
            // comandos que comecam com ":" (:inicio, :fim, :qtd)
            if (linha.startsWith(":")) {
                configurar(linha);
                return;
            }
            if (linha.isEmpty()) return;

            // Divide a aposta pelos espacos
            String[] valores = linha.split("\\s+");
            List<Integer> aposta = new ArrayList<>();
            try {
                for (String valor : valores) aposta.add(Integer.parseInt(valor));

                // ve se a quantidade de numeros esta certa
                if (aposta.size() != quantidade) {
                    enviar("A aposta deve ter exatamente " + quantidade + " numeros.");
                    return;
                }
                // ve se todos os numeros estao dentro do intervalo
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

        // trata os comandos :inicio, :fim e :qtd
        private void configurar(String linha) {
            String[] partes = linha.split("\\s+");
            if (partes.length != 2) {
                enviar("Use :inicio N, :fim N ou :qtd N.");
                return;
            }
            try {
                int valor = Integer.parseInt(partes[1]);
                // comeca com os valores atuais e muda so o que foi pedido
                int novoInicio = inicio;
                int novoFim = fim;
                int novaQuantidade = quantidade;
                switch (partes[0].toLowerCase()) {
                    case ":inicio": novoInicio = valor; break;
                    case ":fim": novoFim = valor; break;
                    case ":qtd": novaQuantidade = valor; break;
                    default: enviar("Comando desconhecido."); return;
                }
                // Valida: inicio e quantidade
                if (novoInicio >= novoFim || novaQuantidade < 1
                        || novaQuantidade > novoFim - novoInicio + 1) {
                    enviar("Configuracao invalida: intervalo ou quantidade incompatível.");
                } else {
                    // Aplica a nova configuracao
                    inicio = novoInicio;
                    fim = novoFim;
                    quantidade = novaQuantidade;
                    enviar("Configuracao atualizada.");
                }
            } catch (NumberFormatException e) {
                enviar("O valor deve ser inteiro.");
            }
        }

        // Roda na thread do sorteador: a cada 60 segundos faz um sorteio e envia o resultado
        private void sortearPeriodicamente() {
            while (ativo) {
                try {
                    // espera 1 minuto entre os sorteios
                    Thread.sleep(60000L);
                    if (!ativo) break;

                    // sorteia os numeros
                    List<Integer> sorteados = sortear();

                    // copia as apostas e limpa a lista para o proximo sorteio
                    List<Integer> minhasApostas;
                    synchronized (apostas) {
                        minhasApostas = new ArrayList<>(apostas);
                        apostas.clear();
                    }

                    // Numeros que estao nos sorteados e nas apostas
                    Set<Integer> acertos = new HashSet<>(sorteados);
                    acertos.retainAll(minhasApostas);
                    enviar("Sorteio: " + sorteados + " | Acertos: " + acertos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        // Sorteia "quantidade" numeros diferentes entre inicio e fim
        private List<Integer> sortear() {
            // monta a lista com todos os numeros do intervalo
            List<Integer> numeros = new ArrayList<>();
            for (int numero = inicio; numero <= fim; numero++) numeros.add(numero);
            // embaralha e pega os primeiros
            Collections.shuffle(numeros, random);
            return new ArrayList<>(numeros.subList(0, quantidade));
        }

        // Envia mensagem ao cliente
        private synchronized void enviar(String mensagem) {
            if (out != null) out.println(mensagem);
        }
    }
}
