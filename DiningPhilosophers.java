
package diningphilophosers;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Semaphore;

public class DiningPhilosophers {

    static final int N = 4;
    static final int ROUNDS = 3;

    static Semaphore[] chopstick = new Semaphore[N];
    static Semaphore room = new Semaphore(N - 1);

    // Shared animation state
    static final Object lock = new Object();
    static String[] state = new String[N];
    static boolean[] stickUsed = new boolean[N];
    static int[] meals = new int[N];

    static TablePanel tablePanel;

    static class Philosopher extends Thread {
        private final int id;

        Philosopher(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try {
                for (int round = 1; round <= ROUNDS; round++) {
                    think();

                    setState(id, "HUNGRY");
                    System.out.println("Philosopher " + id + " is HUNGRY");

                    room.acquire();

                    try {
                        chopstick[id].acquire();
                        setStick(id, true);

                        int next = (id + 1) % N;
                        chopstick[next].acquire();
                        setStick(next, true);

                        try {
                            setState(id, "EATING");
                            System.out.println(
                                "Philosopher " + id + " is EATING"
                            );

                            Thread.sleep(1200);

                            synchronized (lock) {
                                meals[id]++;
                            }

                            System.out.println(
                                "Philosopher " + id + " finished eating"
                            );

                        } finally {
                            setStick(next, false);
                            chopstick[next].release();
                            setStick(id, false);
                            chopstick[id].release();
                        }

                    } finally {
                        room.release();
                    }
                }

                setState(id, "DONE");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                setState(id, "INTERRUPTED");
            }
        }

        private void think() throws InterruptedException {
            setState(id, "THINKING");
            System.out.println("Philosopher " + id + " is THINKING");

            Thread.sleep(1000);
        }
    }

    static void setState(int id, String newState) {
        synchronized (lock) {
            state[id] = newState;
        }
    }

    static void setStick(int id, boolean used) {
        synchronized (lock) {
            stickUsed[id] = used;
        }
    }

    static class TablePanel extends JPanel {

        private final int[][] seats = {
            {350, 100},  // Philosopher 0: top
            {540, 280},  // Philosopher 1: right
            {350, 460},  // Philosopher 2: bottom
            {160, 280}   // Philosopher 3: left
        };

        TablePanel() {
            setPreferredSize(new Dimension(700, 570));
            setBackground(new Color(22, 25, 33));

            // Refresh the animation 30 times per second
            new Timer(33, e -> repaint()).start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            );

            String[] currentState;
            boolean[] currentSticks;
            int[] currentMeals;

            synchronized (lock) {
                currentState = state.clone();
                currentSticks = stickUsed.clone();
                currentMeals = meals.clone();
            }

            // Draw the circular table
            g2.setColor(new Color(77, 66, 56));
            g2.fillOval(205, 135, 290, 290);

            g2.setColor(new Color(151, 127, 100));
            g2.setStroke(new BasicStroke(4));
            g2.drawOval(205, 135, 290, 290);

            g2.setFont(new Font("SansSerif", Font.BOLD, 20));
            g2.setColor(Color.WHITE);
            drawCentered(g2, "DINING TABLE", 350, 270);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            drawCentered(g2, "Semaphore Demo", 350, 295);

            // Chopstick i lies between philosopher i
            // and philosopher (i - 1 + N) % N.
            for (int i = 0; i < N; i++) {
                int previous = (i + N - 1) % N;

                int x1 = seats[i][0];
                int y1 = seats[i][1];
                int x2 = seats[previous][0];
                int y2 = seats[previous][1];

                int sx = (x1 + x2) / 2;
                int sy = (y1 + y2) / 2;

                g2.setStroke(new BasicStroke(
                    currentSticks[i] ? 10 : 6,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
                ));

                g2.setColor(currentSticks[i]
                    ? new Color(255, 190, 65)
                    : new Color(205, 211, 222));

                g2.drawLine(sx - 13, sy - 13, sx + 13, sy + 13);

                g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                g2.setColor(Color.WHITE);
                g2.drawString("C" + i, sx + 15, sy - 8);
            }

            // Draw the philosophers
            for (int i = 0; i < N; i++) {
                int x = seats[i][0];
                int y = seats[i][1];

                Color philosopherColor;

                switch (currentState[i]) {
                    case "THINKING":
                        philosopherColor = new Color(65, 145, 245);
                        break;
                    case "HUNGRY":
                        philosopherColor = new Color(245, 159, 65);
                        break;
                    case "EATING":
                        philosopherColor = new Color(55, 195, 125);
                        break;
                    case "DONE":
                        philosopherColor = new Color(145, 145, 155);
                        break;
                    default:
                        philosopherColor = new Color(225, 85, 85);
                }

                // Philosopher circle
                g2.setColor(new Color(0, 0, 0, 75));
                g2.fillOval(x - 44, y - 42, 92, 92);

                g2.setColor(philosopherColor);
                g2.fillOval(x - 45, y - 45, 90, 90);

                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2));
                g2.drawOval(x - 45, y - 45, 90, 90);

                g2.setFont(new Font("SansSerif", Font.BOLD, 15));
                drawCentered(g2, "P" + i, x, y - 4);

                g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
                drawCentered(g2, currentState[i], x, y + 15);

                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.setColor(Color.WHITE);
                drawCentered(
                    g2,
                    "Meals: " + currentMeals[i] + "/" + ROUNDS,
                    x,
                    y + 64
                );
            }

            // Legend
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.setColor(Color.WHITE);
            g2.drawString("STATUS:", 25, 30);

            drawLegend(g2, 100, 30, new Color(65, 145, 245), "Thinking");
            drawLegend(g2, 220, 30, new Color(245, 159, 65), "Hungry");
            drawLegend(g2, 325, 30, new Color(55, 195, 125), "Eating");
            drawLegend(g2, 420, 30, new Color(255, 190, 65),
                       "Chopstick in use");

            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g2.setColor(new Color(210, 215, 225));
            g2.drawString(
                "Each philosopher eats " + ROUNDS + " times",
                25, 550
            );

            g2.dispose();
        }

        private void drawCentered(
                Graphics2D g2, String text, int x, int y) {
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, x - fm.stringWidth(text) / 2, y);
        }

        private void drawLegend(
                Graphics2D g2, int x, int y, Color color, String label) {
            g2.setColor(color);
            g2.fillOval(x, y - 11, 13, 13);
            g2.setColor(Color.WHITE);
            g2.drawString(label, x + 18, y);
        }
    }

    public static void main(String[] args) {

        for (int i = 0; i < N; i++) {
            chopstick[i] = new Semaphore(1);
            state[i] = "THINKING";
        }

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(
                "Dining Philosophers - Animation"
            );

            tablePanel = new TablePanel();

            frame.add(tablePanel);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            // Start the four philosopher threads
            for (int i = 0; i < N; i++) {
                new Philosopher(i).start();
            }
        });
    }
}
