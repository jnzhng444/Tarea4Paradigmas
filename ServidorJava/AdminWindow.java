package ServidorJava;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Ventana GUI para enviar comandos al servidor (admin).
 * - Caja de texto para escribir comandos y botón Enviar.
 * - Área de salida estilo consola.
 * - Lista "Players" con botón Refrescar (toma ids de SessionRegistry).
 * - Doble clic sobre un player inserta "ADMIN <id> " en la entrada.
 *
 * Nota: Esto es UI (capa de infraestructura), no toca el dominio,
 * por lo que el requisito "sin tipos simples" se mantiene para la API pública del server.
 */
public final class AdminWindow extends JFrame {
    private final CommandDispatcherWithGame dispatcher;
    private final SessionRegistry sessions;

    private final JTextArea outArea = new JTextArea();
    private final JTextField inField = new JTextField();
    private final JButton sendBtn = new JButton("Enviar");
    private final DefaultListModel<String> playersModel = new DefaultListModel<>();
    private final JList<String> playersList = new JList<>(playersModel);
    private final JButton refreshBtn = new JButton("Refrescar");
    private final JButton helpBtn = new JButton("Help");

    public AdminWindow(CommandDispatcherWithGame dispatcher, SessionRegistry sessions) {
        super("Admin Console");
        this.dispatcher = dispatcher;
        this.sessions = sessions;
        buildUI();
        wireEvents();
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
    }

    private void buildUI() {
        var content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(content);

        // Panel izquierda: consola
        outArea.setEditable(false);
        outArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        var scroll = new JScrollPane(outArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Salida"));

        var inputPanel = new JPanel(new BorderLayout(8, 8));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Comando"));
        inputPanel.add(inField, BorderLayout.CENTER);

        var buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonsPanel.add(helpBtn);
        buttonsPanel.add(sendBtn);
        inputPanel.add(buttonsPanel, BorderLayout.EAST);

        var left = new JPanel(new BorderLayout(8, 8));
        left.add(scroll, BorderLayout.CENTER);
        left.add(inputPanel, BorderLayout.SOUTH);

        // Panel derecha: players
        playersList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        var rightScroll = new JScrollPane(playersList);
        rightScroll.setBorder(BorderFactory.createTitledBorder("Players (doble clic inserta en comando)"));

        var rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBtns.add(refreshBtn);

        var right = new JPanel(new BorderLayout(8, 8));
        right.add(rightScroll, BorderLayout.CENTER);
        right.add(rightBtns, BorderLayout.SOUTH);
        right.setPreferredSize(new Dimension(320, 0));

        content.add(left, BorderLayout.CENTER);
        content.add(right, BorderLayout.EAST);

        // Mensaje inicial
        printLine("Consola lista. Escribe un comando y presiona Enter/Enviar. Ejemplos:");
        printLine("  PING");
        printLine("  ADMIN <PLAYER_ID> SPAWN CROCODILE BLUE 4");
        printLine("  ADMIN <PLAYER_ID> SPAWN CROCODILE RED 2 10");
        printLine("  ADMIN <PLAYER_ID> SPAWN FRUIT 3 8 200");
        printLine("  ADMIN <PLAYER_ID> DELETE FRUIT 3 8");
        printLine("Usa 'Refrescar' para ver IDs activos y hacer doble clic en uno.");
        printLine("");
    }

    private void wireEvents() {
        // Enter en campo de texto
        inField.addActionListener(e -> sendCurrentCommand());
        sendBtn.addActionListener(e -> sendCurrentCommand());
        helpBtn.addActionListener(e -> showHelp());
        refreshBtn.addActionListener(e -> refreshPlayers());

        // Doble clic en lista -> insertar plantilla ADMIN <id>
        playersList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    var idx = playersList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        var id = playersModel.get(idx);
                        // si la línea ya tiene texto, respetamos lo anterior
                        var base = inField.getText();
                        if (base == null || base.isBlank()) {
                            inField.setText("ADMIN " + id + " ");
                        } else {
                            inField.setText(base + " " + id + " ");
                        }
                        inField.requestFocusInWindow();
                        inField.setCaretPosition(inField.getText().length());
                    }
                }
            }
        });
    }

    private void sendCurrentCommand() {
        var line = inField.getText();
        if (line == null) line = "";
        line = line.trim();
        if (line.isEmpty()) return;

        // ClientContext que escribe en el área de salida
        var writer = new PrintWriter(new TextAreaOutputStream(outArea), true, StandardCharsets.UTF_8);
        var ctx = new ClientContext(writer);

        try {
            String resp = dispatcher.dispatch(line, ctx);
            if (resp != null && !resp.isBlank()) {
                printLine(resp);
            }
        } catch (Exception ex) {
            printLine("ERR 500 " + ex.getMessage());
        } finally {
            inField.setText("");
            inField.requestFocusInWindow();
        }
    }

    private void showHelp() {
        printLine("Comandos disponibles:");
        printLine("  PING");
        printLine("  ADMIN <PLAYER_ID> SPAWN CROCODILE RED <LIANA> <ALTURA>");
        printLine("  ADMIN <PLAYER_ID> SPAWN CROCODILE BLUE <LIANA>");
        printLine("  ADMIN <PLAYER_ID> SPAWN FRUIT <LIANA> <ALTURA> <PUNTOS>");
        printLine("  ADMIN <PLAYER_ID> DELETE FRUIT <LIANA> <ALTURA>");
        printLine("Tips:");
        printLine("  • Usa 'Refrescar' y doble clic para insertar <PLAYER_ID>.");
        printLine("");
    }

    private void refreshPlayers() {
        playersModel.clear();
        List<ClientContext> list = sessions.list();
        for (ClientContext s : list) {
            if (s.role() == Role.PLAYER && s.playerId() != null) {
                playersModel.addElement(s.playerId().value());
            }
        }
        printLine("Players activos: " + playersModel.getSize());
    }

    private void printLine(String s) {
        outArea.append(s);
        outArea.append("\n");
        outArea.setCaretPosition(outArea.getDocument().getLength());
    }

    /** OutputStream que escribe en el JTextArea en EDT. */
    private static final class TextAreaOutputStream extends OutputStream {
        private final JTextArea target;
        public TextAreaOutputStream(JTextArea target) { this.target = target; }
        @Override public void write(int b) {
            append(new String(new byte[]{(byte)b}, StandardCharsets.UTF_8));
        }
        @Override public void write(byte[] b, int off, int len) {
            append(new String(b, off, len, StandardCharsets.UTF_8));
        }
        private void append(String s) {
            if (SwingUtilities.isEventDispatchThread()) {
                target.append(s);
                target.setCaretPosition(target.getDocument().getLength());
            } else {
                SwingUtilities.invokeLater(() -> {
                    target.append(s);
                    target.setCaretPosition(target.getDocument().getLength());
                });
            }
        }
    }

    /** Muestra la ventana (EDT). */
    public void showWindow() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }
}
