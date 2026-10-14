package net.feelix.stagecoach.merger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;

public final class MergerApp {
    private MergerApp() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 0 || GraphicsEnvironment.isHeadless()) {
            System.exit(cli(args));
        }
        SwingUtilities.invokeLater(MergerApp::window);
    }

    static int cli(String[] args) {
        try {
            byte[] pack = PackMerger.stagecoachPack();
            if (args.length >= 2 && args[0].equals("--export")) {
                print(PackMerger.export(pack, Path.of(args[1])));
                return 0;
            }
            if (args.length == 0 || args[0].startsWith("-")) {
                System.out.println("""
                        Stagecoach Pack Merger - adds Stagecoach's look to your server's resource pack.

                          java -jar Stagecoach-pack-merger.jar <your pack .zip or folder> [output .zip]
                              Writes a copy of your pack with Stagecoach in it (default: "<your pack> + Stagecoach.zip").
                              Run it again after updating Stagecoach: the old Stagecoach files are replaced.
                          java -jar Stagecoach-pack-merger.jar --export <output .zip>
                              Writes Stagecoach's pack on its own.

                        Then use the new zip as your server's resource pack (server.properties: resource-pack and
                        resource-pack-sha1) and set resourcePack.send: false in plugins/Stagecoach/config.yml.""");
                return args.length == 0 ? 0 : 2;
            }
            Path source = Path.of(args[0]);
            Path out = args.length >= 2 ? Path.of(args[1]) : defaultOutput(source);
            print(PackMerger.merge(pack, source, out));
            return 0;
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
            return 1;
        }
    }

    private static void print(PackMerger.Result r) {
        System.out.println(report(r));
    }

    static String report(PackMerger.Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Written: ").append(r.output().toAbsolutePath()).append('\n');
        if (r.kept() > 0) sb.append("Your files kept: ").append(r.kept()).append('\n');
        sb.append("Stagecoach files added: ").append(r.added()).append('\n');
        sb.append("SHA-1: ").append(r.sha1()).append('\n');
        for (String note : r.notes()) sb.append('\n').append("Note: ").append(note).append('\n');
        sb.append('\n').append("Next: use this zip as your server's resource pack - in server.properties set resource-pack "
                + "to its download link and resource-pack-sha1=").append(r.sha1()).append(" - and set resourcePack.send: "
                + "false in plugins/Stagecoach/config.yml so players don't download Stagecoach twice.");
        return sb.toString();
    }

    static Path defaultOutput(Path source) {
        String name = source.getFileName().toString();
        if (name.toLowerCase().endsWith(".zip")) name = name.substring(0, name.length() - 4);
        Path dir = source.toAbsolutePath().getParent();
        return dir.resolve(name + " + Stagecoach.zip");
    }

    private static void window() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // the default look is fine
        }
        JFrame frame = new JFrame("Stagecoach Pack Merger");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JLabel intro = new JLabel("<html><b>Add Stagecoach to your server's resource pack.</b><br>"
                + "Your files are kept; Stagecoach's coaches, icons and travel screen are added. Run it again after "
                + "updating Stagecoach to update them.</html>");
        JTextArea log = new JTextArea(12, 60);
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);

        JButton merge = new JButton("Choose your pack and add Stagecoach...");
        merge.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Your resource pack (.zip or folder)");
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            chooser.setFileFilter(new FileNameExtensionFilter("Resource packs (.zip or folder)", "zip"));
            if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
            File source = chooser.getSelectedFile();
            JFileChooser save = new JFileChooser(source.getParentFile());
            save.setDialogTitle("Save the pack with Stagecoach as");
            save.setSelectedFile(defaultOutput(source.toPath()).toFile());
            if (save.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
            run(log, () -> report(PackMerger.merge(PackMerger.stagecoachPack(), source.toPath(), save.getSelectedFile().toPath())));
        });
        JButton export = new JButton("Save Stagecoach's pack on its own...");
        export.addActionListener(e -> {
            JFileChooser save = new JFileChooser();
            save.setSelectedFile(new File("Stagecoach resource pack.zip"));
            if (save.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
            run(log, () -> report(PackMerger.export(PackMerger.stagecoachPack(), save.getSelectedFile().toPath())));
        });

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(merge);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(export);

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.add(intro, BorderLayout.NORTH);
        top.add(buttons, BorderLayout.SOUTH);
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(log), BorderLayout.CENTER);
        frame.setContentPane(root);
        frame.setMinimumSize(new Dimension(640, 360));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private interface Work {
        String run() throws Exception;
    }

    private static void run(JTextArea log, Work work) {
        try {
            log.setText(work.run());
        } catch (Exception ex) {
            log.setText("Failed: " + ex.getMessage());
        }
        log.setCaretPosition(0);
    }
}
