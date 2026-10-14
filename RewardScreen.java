package sudokuBySabel;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * Displays the flower reward and idol-selection screens.
 *
 * Flow:
 * Victory -> Male/Female selection -> Character -> Thank you
 * -> Play Again -> existing intro screen.
 *
 * This class does not change the assignment's file processing.
 */
public final class RewardScreen {

    private RewardScreen() {
    }

    /**
     * Opens the reward after the player has solved the puzzle.
     *
     * The dialog is modal, so the Sudoku board cannot be clicked
     * while the player is choosing an idol.
     *
     * @param owner the existing Sudoku window
     * @param returnToIntro action that opens the intro again
     */
    public static void open(
            JFrame owner, Runnable returnToIntro) {

        // Load every required image before opening the reward.
        // If an image is missing, the Sudoku window stays available.
        Map<String, BufferedImage> images = new HashMap<>();

        String[] filenames = {
                "victory.png",
                "male_idols.png",
                "female_idols.png",
                "lily.png",
                "mei.png",
                "sofia.png",
                "oliver.png",
                "ren.png",
                "mateo.png"
        };

        try {
            for (String filename : filenames) {
                URL resource = RewardScreen.class.getResource(
                        "/sudokuBySabel/" + filename
                );

                if (resource == null) {
                    throw new IOException(
                            "Missing image: " + filename
                            + "\nPut it inside src/sudokuBySabel."
                    );
                }

                BufferedImage image = ImageIO.read(resource);

                if (image == null) {
                    throw new IOException(
                            "Cannot read image: " + filename
                    );
                }

                images.put(filename, image);
            }

        } catch (IOException exception) {
            JOptionPane.showMessageDialog(
                    owner,
                    exception.getMessage(),
                    "Reward image error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        RewardDialog dialog =
                new RewardDialog(owner, images, returnToIntro);

        dialog.setVisible(true);
    }

    /**
     * One dialog is reused for all reward pages.
     */
    private static class RewardDialog extends JDialog {

        private static final long serialVersionUID = 1L;

        private final Map<String, BufferedImage> images;
        private final Runnable returnToIntro;

        RewardDialog(
                JFrame owner,
                Map<String, BufferedImage> images,
                Runnable returnToIntro) {

            super(owner, "Your Flower Reward", true);

            this.images = images;
            this.returnToIntro = returnToIntro;

            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            showVictory();
            pack();

            Rectangle screen = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getMaximumWindowBounds();

            setSize(
                    Math.min(getWidth(), screen.width),
                    Math.min(getHeight(), screen.height)
            );

            setLocationRelativeTo(owner);
        }

        /**
         * Flower reward page.
         * Left button: female idols.
         * Right button: male idols.
         */
        private void showVictory() {
            ArtworkPanel panel =
                    new ArtworkPanel(images.get("victory.png"));

            panel.addHotspot(
                    "Choose Female Idol",
                    0.025, 0.700, 0.325, 0.105,
                    () -> showIdols(false)
            );

            panel.addHotspot(
                    "Choose Male Idol",
                    0.655, 0.700, 0.325, 0.105,
                    () -> showIdols(true)
            );

            display(panel);
        }

        /**
         * Each character and their nameplate are clickable.
         *
         * Male, left to right: Oliver, Ren, Mateo.
         * Female, left to right: Lily, Mei, Sofia.
         */
        private void showIdols(boolean male) {
            String filename = male
                    ? "male_idols.png"
                    : "female_idols.png";

            String[] names = male
                    ? new String[] {"Oliver", "Ren", "Mateo"}
                    : new String[] {"Lily", "Mei", "Sofia"};

            String[] portraits = male
                    ? new String[] {
                            "oliver.png", "ren.png", "mateo.png"
                    }
                    : new String[] {
                            "lily.png", "mei.png", "sofia.png"
                    };

            ArtworkPanel panel =
                    new ArtworkPanel(images.get(filename));

            // Non-overlapping areas covering each character.
            double[] left = {0.12, 0.40, 0.60};
            double[] width = {0.28, 0.20, 0.22};

            for (int i = 0; i < names.length; i++) {
                final String portrait = portraits[i];
                final String name = names[i];

                panel.addHotspot(
                        "Give your flower to " + name,
                        left[i], 0.07, width[i], 0.92,
                        () -> showThankYou(name, portrait)
                );
            }

            // A visible navigation button outside the artwork.
            JButton back = new JButton("Back to flower reward");
            back.addActionListener(event -> showVictory());

            JPanel navigation = new JPanel();
            navigation.setBackground(new Color(237, 222, 249));
            navigation.add(back);

            JPanel page = new JPanel(new BorderLayout());
            page.add(panel, BorderLayout.CENTER);
            page.add(navigation, BorderLayout.SOUTH);

            display(page);
        }

        /**
         * Displays the selected idol's thank-you image.
         * The Play Again hotspot matches each image's artwork.
         */
        private void showThankYou(
                String name, String filename) {

            ArtworkPanel panel =
                    new ArtworkPanel(images.get(filename));

            double x;
            double y;
            double width;
            double height = 0.115;

            switch (name) {
                case "Lily":
                    x = 0.582;
                    y = 0.725;
                    width = 0.275;
                    break;

                case "Mei":
                    x = 0.590;
                    y = 0.685;
                    width = 0.245;
                    break;

                case "Sofia":
                    x = 0.570;
                    y = 0.695;
                    width = 0.295;
                    break;

                case "Oliver":
                    x = 0.560;
                    y = 0.695;
                    width = 0.285;
                    break;

                case "Ren":
                    x = 0.575;
                    y = 0.725;
                    width = 0.275;
                    break;

                default: // Mateo
                    x = 0.560;
                    y = 0.675;
                    width = 0.275;
                    break;
            }

            panel.addHotspot(
                    "Play Again",
                    x, y, width, height,
                    () -> {
                        dispose();
                        returnToIntro.run();
                    }
            );

            display(panel);
        }

        private void display(JComponent page) {
            // Avoid retaining a default button from another page.
            getRootPane().setDefaultButton(null);

            setContentPane(page);
            revalidate();
            repaint();
        }
    }

    /**
     * Shows an image without stretching or cropping.
     * Transparent buttons stay aligned when the window is resized.
     */
    private static class ArtworkPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        private final BufferedImage image;
        private final List<Hotspot> hotspots = new ArrayList<>();

        ArtworkPanel(BufferedImage image) {
            this.image = image;

            setLayout(null);
            setBackground(new Color(237, 222, 249));

            int width = 1100;
            int height = (int) Math.round(
                    width * (double) image.getHeight()
                    / image.getWidth()
            );

            setPreferredSize(new Dimension(width, height));
        }

        void addHotspot(
                String label,
                double x,
                double y,
                double width,
                double height,
                Runnable action) {

            JButton button = new JButton();

            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
            button.setFocusPainted(false);

            button.setCursor(
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            );

            button.setToolTipText(label);
            button.getAccessibleContext().setAccessibleName(label);
            button.addActionListener(event -> action.run());

            hotspots.add(
                    new Hotspot(button, x, y, width, height)
            );

            add(button);
        }

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

        @Override
        public void doLayout() {
            Rectangle bounds = imageBounds();

            for (Hotspot hotspot : hotspots) {
                hotspot.button.setBounds(
                        bounds.x + (int) Math.round(
                                bounds.width * hotspot.x),
                        bounds.y + (int) Math.round(
                                bounds.height * hotspot.y),
                        (int) Math.round(
                                bounds.width * hotspot.width),
                        (int) Math.round(
                                bounds.height * hotspot.height)
                );
            }
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

    /**
     * Stores a button's position as fractions of the image size.
     */
    private static class Hotspot {
        final JButton button;
        final double x;
        final double y;
        final double width;
        final double height;

        Hotspot(
                JButton button,
                double x,
                double y,
                double width,
                double height) {

            this.button = button;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}