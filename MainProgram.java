package sudokuBySabel;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * ファイルから数独問題を読み込んで自動解析（ソルバー）を行い、解答ファイルを保存するとともに、
 * Swing GUI画面上で読み込んだ問題を選択してプレイできるようにするメインプログラムクラス。
 *
 * 【仕様・設定】
 *  ・設定ファイル: カレントフォルダ/.sdk
 *      1行目: 問題フォルダ名
 *      2行目: 回答フォルダ名
 *  ・入力ファイル: *.udk (CSV形式、9行9列) ※ 0または空欄は空白マス
 *  ・出力ファイル: [問題名].edk (CSV形式、9行9列) ※ 解けない場合は0バイトの空ファイル
 */
public class MainProgram {

    /** 文字色定義: 通常テキスト用（紫色） */
    private static final Color PURPLE = new Color(74, 42, 100);
    /** 文字色定義: 正解時テキスト用（緑色） */
    private static final Color GREEN = new Color(20, 130, 60);
    /** 文字色定義: 不正解時テキスト用（赤色） */
    private static final Color RED = new Color(200, 35, 55);

    /**
     * 読み込んだ1つのパズル情報（問題名、出力先パス、問題盤面、解答盤面）を保持する内部データ構造クラス。
     */
    private static class PuzzleFile {
        /** 問題ファイル名 */
        final String name;
        /** 解答ファイルの出力先パス */
        final Path output;
        /** 問題の初期盤面（9x9） */
        final int[][] clues;
        /** 自動解析された解答盤面（解けない場合は null） */
        final int[][] solution;

        /**
         * PuzzleFile オブジェクトを構築します。
         *
         * @param name     問題ファイル名
         * @param output   解答ファイルの保存先パス
         * @param clues    問題盤面データ
         * @param solution 解答盤面データ（解なしの場合は null）
         */
        PuzzleFile(
                String name,
                Path output,
                int[][] clues,
                int[][] solution) {

            this.name = name;
            this.output = output;
            this.clues = clues;
            this.solution = solution;
        }

        /**
         * ドロップダウンリスト（JComboBox）表示用の文字列を返します。
         *
         * @return パズル名（解がない場合は注記を追加）
         */
        @Override
        public String toString() {
            return name + (solution == null ? " (解なし)" : "");
        }
    }

    /**
     * プログラムのエントリポイント。
     * GUI画面を開く前に、全問題ファイルの読み込み・解の計算・解答ファイルの保存を一括実行します。
     *
     * @param args コマンドライン引数（未使用）
     */
    public static void main(String[] args) {
        try {
            List<PuzzleFile> puzzles = loadAndSolveAll();

            if (puzzles.isEmpty()) {
                System.out.println(
                        "プレイ可能な問題がありません。ファイル処理を終了します。"
                );
                return;
            }

            // Swing GUIをイベントディスパッチスレッド上で起動
            SwingUtilities.invokeLater(() -> showIntro(puzzles));

        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("エラー: " + exception.getMessage());
        }
    }

    /**
     * カレントフォルダの .sdk 設定ファイルからフォルダパスを読み込み、
     * すべての .udk 問題ファイルを解析・解答して .edk ファイルを出力します。
     *
     * @return 読み込みに成功したパズルオブジェクトのリスト
     * @throws IOException 設定ファイルが存在しない、または読み込み失敗時
     */
    private static List<PuzzleFile> loadAndSolveAll()
            throws IOException {

        Path current = Paths.get("").toAbsolutePath().normalize();
        Path config = current.resolve(".sdk");

        System.out.println("カレントフォルダ: " + current);

        if (!Files.isRegularFile(config)) {
            throw new IOException(
                    "設定ファイルが見つかりません: " + config
            );
        }

        List<String> settings =
                Files.readAllLines(config, StandardCharsets.UTF_8);

        if (settings.size() != 2) {
            throw new IOException(
                    ".sdk ファイルは必ず2行（1行目:問題フォルダ, 2行目:回答フォルダ）で記述してください。"
            );
        }

        String inputName = removeBom(settings.get(0)).trim();
        String outputName = settings.get(1).trim();

        if (inputName.isEmpty() || outputName.isEmpty()) {
            throw new IOException("フォルダパスを空にすることはできません。");
        }

        // 絶対パスはそのまま、相対パスはカレントフォルダ起点で解決
        Path inputFolder = current.resolve(inputName).normalize();
        Path outputFolder = current.resolve(outputName).normalize();

        if (!Files.isDirectory(inputFolder)) {
            throw new IOException(
                    "問題フォルダが見つかりません: " + inputFolder
            );
        }

        Files.createDirectories(outputFolder);

        List<Path> files;

        // 問題フォルダ内の .udk ファイルを探索してソート
        try (Stream<Path> stream = Files.list(inputFolder)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(path ->
                            path.getFileName().toString().endsWith(".udk"))
                    .sorted(Comparator.comparing(
                            path -> path.getFileName().toString()))
                    .collect(Collectors.toList());
        }

        List<PuzzleFile> puzzles = new ArrayList<>();

        // 問題ファイルが0個の場合も正常終了
        if (files.isEmpty()) {
            System.out.println("問題ファイルが0件です。正常終了します。");
            return puzzles;
        }

        for (Path input : files) {
            String name = input.getFileName().toString();

            // 拡張子 .udk を .edk に置換
            String answerName =
                    name.substring(0, name.length() - 4) + ".edk";

            Path output = outputFolder.resolve(answerName);

            int[][] clues = null;
            int[][] solution = null;

            try {
                clues = readBoard(input);
                // ソルバーを実行して解を取得（解けない場合は null）
                solution = SudokuEngine.solve(clues);

            } catch (IOException | IllegalArgumentException exception) {
                System.err.println(
                        name + ": " + exception.getMessage()
                );
            }

            try {
                // 解答を保存（解けない場合や不正フォーマットの場合は空ファイルを生成）
                writeAnswer(output, solution);

                System.out.println(
                        name + " -> " + output
                        + (solution == null
                                ? " [空ファイルを出力]"
                                : " [解析成功]")
                );

            } catch (IOException exception) {
                System.err.println(
                        "保存失敗 " + output
                        + ": " + exception.getMessage()
                );
            }

            // フォーマットが正常な問題のみ画面表示リストに追加
            if (clues != null) {
                puzzles.add(
                        new PuzzleFile(name, output, clues, solution)
                );
            }
        }

        return puzzles;
    }

    /**
     * .udk ファイルから 9行9列 のCSV形式データを読み込み、2次元配列に変換します。
     *
     * @param file 読み込む問題ファイルのパス
     * @return 9x9 の盤面配列（0 は空欄）
     * @throws IOException ファイル読込エラー時
     * @throws IllegalArgumentException フォーマット（行数・列数・数値）が不正な場合
     */
    private static int[][] readBoard(Path file) throws IOException {
        List<String> lines =
                Files.readAllLines(file, StandardCharsets.UTF_8);

        if (lines.size() != 9) {
            throw new IllegalArgumentException(
                    "問題ファイルは必ず9行で構成されている必要があります。"
            );
        }

        int[][] board = new int[9][9];

        for (int row = 0; row < 9; row++) {
            String line = row == 0
                    ? removeBom(lines.get(row))
                    : lines.get(row);

            // カンマ区切りで分割（末尾の空要素も保持）
            String[] values = line.split(",", -1);

            if (values.length != 9) {
                throw new IllegalArgumentException(
                        (row + 1) + " 行目の列数が9列ではありません。"
                );
            }

            for (int col = 0; col < 9; col++) {
                String value = values[col].trim();

                if (value.isEmpty()) {
                    board[row][col] = 0;
                } else if (value.matches("[0-9]")) {
                    board[row][col] = Integer.parseInt(value);
                } else {
                    throw new IllegalArgumentException(
                            "不正な値が存在します (行 " + (row + 1)
                            + ", 列 " + (col + 1) + ")"
                    );
                }
            }
        }

        return board;
    }

    /**
     * 文字列の先頭に含まれる BOM（Byte Order Mark: \uFEFF）を除去します。
     *
     * @param text 対象の文字列
     * @return BOMを除去した文字列
     */
    private static String removeBom(String text) {
        return text.startsWith("\uFEFF")
                ? text.substring(1)
                : text;
    }

    /**
     * 解答盤面を 9x9 のCSV形式（.edk）でファイルに書き出します。
     * 解答が null の場合は 0バイトの空ファイルを生成します。
     *
     * @param file  書き出し先のファイルパス
     * @param board 出力する解答盤面（解なしの場合は null）
     * @throws IOException ファイル書き込みエラー時
     */
    private static void writeAnswer(Path file, int[][] board)
            throws IOException {

        if (board == null) {
            // 解けない場合は空ファイル（0バイト）を作成
            Files.write(file, new byte[0]);
            return;
        }

        List<String> lines = new ArrayList<>();

        for (int row = 0; row < 9; row++) {
            StringBuilder line = new StringBuilder();

            for (int col = 0; col < 9; col++) {
                if (col > 0) {
                    line.append(",");
                }
                line.append(board[row][col]);
            }

            lines.add(line.toString());
        }

        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /**
     * 読み込んだ問題を選択して画面上で遊ぶための GUI 画面を構築・表示します。
     *
     * @param puzzles 画面に読み込むパズルリスト
     */
    private static void showGame(List<PuzzleFile> puzzles) {
        JFrame window = new JFrame("Sudoku by Sabel");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        BackgroundPanel background = new BackgroundPanel();
        background.setLayout(new GridBagLayout());
        background.setPreferredSize(new Dimension(1100, 820));
        background.setBorder(
                BorderFactory.createEmptyBorder(185, 20, 20, 20)
        );

        JComboBox<PuzzleFile> selection =
                new JComboBox<>(puzzles.toArray(new PuzzleFile[0]));

        JPanel boardPanel = new JPanel(new GridLayout(9, 9));
        Dimension boardSize = new Dimension(450, 450);
        boardPanel.setPreferredSize(boardSize);
        boardPanel.setMinimumSize(boardSize);

        JTextField[][] cells = new JTextField[9][9];

        JLabel status = new JLabel(
                "緑色 = 正解 / 赤色 = 不正解"
        );
        status.setForeground(PURPLE);

        JButton checkButton = new JButton("答え合わせと保存");
        JButton resetButton = new JButton("リセット");

        /**
         * ドロップダウンで選択された問題を取り出し、GUI盤面を再構築するレンダリング処理。
         */
        Runnable displaySelected = () -> {
            PuzzleFile puzzle =
                    (PuzzleFile) selection.getSelectedItem();

            if (puzzle == null) {
                return;
            }

            boardPanel.removeAll();

            for (int row = 0; row < 9; row++) {
                for (int col = 0; col < 9; col++) {
                    JTextField cell = new JTextField();

                    cell.setHorizontalAlignment(JTextField.CENTER);
                    cell.setFont(
                            new Font("SansSerif", Font.BOLD, 23)
                    );

                    // 3x3 ブロックの区切り線を太く設定
                    cell.setBorder(
                            BorderFactory.createMatteBorder(
                                    row % 3 == 0 ? 3 : 1,
                                    col % 3 == 0 ? 3 : 1,
                                    row == 8 ? 3 : 0,
                                    col == 8 ? 3 : 0,
                                    new Color(125, 86, 151)
                            )
                    );

                    int clue = puzzle.clues[row][col];
                    boolean editable =
                            clue == 0 && puzzle.solution != null;

                    cell.setEditable(editable);
                    cell.setForeground(PURPLE);
                    cell.setBackground(
                            editable
                                    ? new Color(255, 250, 255)
                                    : new Color(233, 220, 245)
                    );

                    if (clue != 0) {
                        cell.setText(String.valueOf(clue));
                    }

                    if (editable) {
                        addColorCheck(
                                cell, puzzle.solution[row][col]
                        );
                    }

                    cells[row][col] = cell;
                    boardPanel.add(cell);
                }
            }

            checkButton.setEnabled(puzzle.solution != null);

            status.setText(
                    puzzle.solution == null
                            ? "この問題には解が存在しません。"
                            : "緑色 = 正解 / 赤色 = 不正解"
            );

            boardPanel.revalidate();
            boardPanel.repaint();
        };

        // イベントリスナーの登録
        selection.addActionListener(event -> displaySelected.run());
        resetButton.addActionListener(event -> displaySelected.run());

        /**
         * プレイヤーが入力した解答を検証し、正解であれば .edk ファイルへ再出力する処理。
         */
        checkButton.addActionListener(event -> {
            PuzzleFile puzzle =
                    (PuzzleFile) selection.getSelectedItem();

            if (puzzle == null || puzzle.solution == null) {
                return;
            }

            int[][] answer = new int[9][9];

            for (int row = 0; row < 9; row++) {
                for (int col = 0; col < 9; col++) {
                    String text = cells[row][col].getText().trim();

                    if (!text.matches("[1-9]")) {
                        JOptionPane.showMessageDialog(
                                window,
                                "すべてのマスに1〜9の数字を入力してください。"
                        );
                        return;
                    }

                    answer[row][col] = Integer.parseInt(text);
                }
            }

            if (!SudokuEngine.isComplete(answer)) {
                JOptionPane.showMessageDialog(
                        window, "数字に重複・矛盾があります。やり直してください！"
                );
                return;
            }

            try {
                // Preserve the assignment requirement:
                // save the completed answer as a 9x9 CSV .edk file.
                writeAnswer(puzzle.output, answer);

            } catch (IOException exception) {
                JOptionPane.showMessageDialog(
                        window,
                        "Could not save the answer:\n"
                        + exception.getMessage(),
                        "Save error",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            // Only reached after the board has passed validation
            // and the answer has been saved successfully.
            RewardScreen.open(window, () -> {
                // Play Again returns to the existing introduction.
                showIntro(puzzles);

                // Close the completed Sudoku window.
                window.dispose();
            });
        });

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.add(checkButton);
        buttons.add(resetButton);

        GridBagConstraints layout = new GridBagConstraints();
        layout.gridx = 0;
        layout.insets = new Insets(0, 0, 10, 0);

        layout.gridy = 0;
        background.add(selection, layout);

        layout.gridy = 1;
        background.add(boardPanel, layout);

        layout.gridy = 2;
        background.add(status, layout);

        layout.gridy = 3;
        background.add(buttons, layout);

        displaySelected.run();

        // 画面に収まらない場合に備えてスクロールペインを設定
        JScrollPane scrollPane = new JScrollPane(background);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);

        window.setContentPane(scrollPane);
        window.pack();

        Rectangle screen = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();

        window.setSize(
                Math.min(window.getWidth(), screen.width),
                Math.min(window.getHeight(), screen.height)
        );
        window.setLocationRelativeTo(null);
        window.setVisible(true);
        
    }

    /**
     * ユーザー入力値と自動解析解をリアルタイム比較し、フォント色（緑/赤）を動的に変更します。
     *
     * @param cell          対象のテキストフィールド
     * @param correctAnswer 正解の数値
     */
    private static void addColorCheck(
            JTextField cell, int correctAnswer) {

        cell.getDocument().addDocumentListener(
                new DocumentListener() {
                    /**
                     * 入力状態に応じてテキストの色を更新します。
                     */
                    private void updateColor() {
                        String text = cell.getText().trim();

                        if (text.isEmpty()) {
                            cell.setForeground(PURPLE);
                        } else if (text.equals(
                                String.valueOf(correctAnswer))) {
                            cell.setForeground(GREEN);
                        } else {
                            cell.setForeground(RED);
                        }
                    }

                    @Override
                    public void insertUpdate(DocumentEvent event) {
                        updateColor();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent event) {
                        updateColor();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent event) {
                        // プレーンテキストフィールドのため不要
                    }
                }
        );
    }

    /**
     * 背景画像を表示するためのカスタム JPanel クラス。
     * 画像が存在しない場合でも、単色の背景色で安全にフォールバックします。
     */
    private static class BackgroundPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private BufferedImage image;

        /**
         * BackgroundPanel を構築し、背景画像をリソースからロードします。
         */
        BackgroundPanel() {
            setBackground(new Color(237, 222, 249));

            try {
                java.net.URL resource =
                        MainProgram.class.getResource(
                                "/sudokuBySabel/background.png"
                        );

                if (resource != null) {
                    image = ImageIO.read(resource);
                }
            } catch (IOException exception) {
                System.err.println(
                        "背景画像の読み込みに失敗しました。"
                );
            }
        }

        /**
         * パネルの描画処理。背景画像をアスペクト比を保ったまま描画します。
         *
         * @param graphics 描画用グラフィックスコンテキスト
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            if (image == null) {
                return;
            }

            Graphics2D g = (Graphics2D) graphics.create();

            try {
                g.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR
                );

                double scale = Math.max(
                        (double) getWidth() / image.getWidth(),
                        (double) getHeight() / image.getHeight()
                );

                int width = (int) Math.ceil(image.getWidth() * scale);
                int height = (int) Math.ceil(image.getHeight() * scale);

                g.drawImage(
                        image,
                        (getWidth() - width) / 2,
                        (getHeight() - height) / 2,
                        width,
                        height,
                        this
                );
            } finally {
                g.dispose();
            }
        }
    }
    /**
     * Opens the introduction screen.
     * Clicking Start Game opens the existing Sudoku window.
     */
    private static void showIntro(List<PuzzleFile> puzzles) {
        final BufferedImage introImage;

        try {
            java.net.URL resource = MainProgram.class.getResource(
                    "/sudokuBySabel/intro.png"
            );

            if (resource == null) {
                throw new IOException(
                        "Put intro.png inside src/sudokuBySabel."
                );
            }

            introImage = ImageIO.read(resource);

            if (introImage == null) {
                throw new IOException("Could not read intro.png.");
            }

        } catch (IOException exception) {
            JOptionPane.showMessageDialog(
                    null,
                    exception.getMessage(),
                    "Introduction image error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        JFrame introWindow = new JFrame("Sudoku by Sabel");
        introWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        IntroPanel introPanel = new IntroPanel(
                introImage,
                () -> {
                    // Open the existing Sudoku page.
                    showGame(puzzles);

                    // Close only the introduction window.
                    introWindow.dispose();
                }
        );

        introWindow.setContentPane(introPanel);

        // Enter also activates the Start Game button.
        introWindow.getRootPane().setDefaultButton(
                introPanel.startButton
        );

        introWindow.pack();

        // Keep the window within the available screen area.
        Rectangle screen = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();

        introWindow.setSize(
                Math.min(introWindow.getWidth(), screen.width),
                Math.min(introWindow.getHeight(), screen.height)
        );

        introWindow.setLocationRelativeTo(null);
        introWindow.setVisible(true);

        introPanel.startButton.requestFocusInWindow();
    }

    /**
     * Displays the full intro image without stretching or cropping it.
     *
     * The transparent button follows the Start Game artwork when
     * the window is resized.
     */
    private static class IntroPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        private final BufferedImage image;
        private final JButton startButton;

        IntroPanel(BufferedImage image, Runnable onStart) {
            this.image = image;

            // Position the button relative to the displayed image.
            setLayout(null);
            setBackground(new Color(237, 222, 249));

            int preferredWidth = 1100;
            int preferredHeight = (int) Math.round(
                    preferredWidth
                    * (double) image.getHeight()
                    / image.getWidth()
            );

            setPreferredSize(
                    new Dimension(preferredWidth, preferredHeight)
            );

            // The image already supplies the button's visible design.
            startButton = new JButton();
            startButton.setOpaque(false);
            startButton.setContentAreaFilled(false);
            startButton.setBorderPainted(false);
            startButton.setFocusPainted(false);

            startButton.setCursor(
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            );
            startButton.setToolTipText("Start Game");
            startButton.getAccessibleContext()
                    .setAccessibleName("Start Game");

            startButton.addActionListener(event -> onStart.run());

            add(startButton);
        }

        /**
         * Calculates the image bounds using its original aspect ratio.
         * Any extra space becomes a lavender margin.
         */
        private Rectangle imageBounds() {
            double scale = Math.min(
                    (double) getWidth() / image.getWidth(),
                    (double) getHeight() / image.getHeight()
            );

            int width = (int) Math.round(image.getWidth() * scale);
            int height = (int) Math.round(image.getHeight() * scale);

            return new Rectangle(
                    (getWidth() - width) / 2,
                    (getHeight() - height) / 2,
                    width,
                    height
            );
        }

        /**
         * Aligns the clickable area with the Start Game artwork.
         *
         * These proportions match the image you attached:
         * left = 41%, top = 47%, width = 18%, height = 13%.
         */
        @Override
        public void doLayout() {
            Rectangle bounds = imageBounds();

            startButton.setBounds(
                    bounds.x + (int) Math.round(bounds.width * 0.41),
                    bounds.y + (int) Math.round(bounds.height * 0.47),
                    (int) Math.round(bounds.width * 0.18),
                    (int) Math.round(bounds.height * 0.13)
            );
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            Graphics2D g = (Graphics2D) graphics.create();

            try {
                g.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR
                );

                Rectangle bounds = imageBounds();

                g.drawImage(
                        image,
                        bounds.x,
                        bounds.y,
                        bounds.width,
                        bounds.height,
                        this
                );
            } finally {
                g.dispose();
            }
        }
    }
}