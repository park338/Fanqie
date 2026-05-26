package cn.fanqie.pomodoro.ui;

import cn.fanqie.pomodoro.core.PomodoroListener;
import cn.fanqie.pomodoro.core.PomodoroTimer;
import cn.fanqie.pomodoro.model.PomodoroConfig;
import cn.fanqie.pomodoro.model.SessionType;
import cn.fanqie.pomodoro.model.TimerStatus;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class MainFrame extends JFrame implements PomodoroListener {
    private static final int UI_TICK_MS = 1000;

    private final PomodoroTimer pomodoroTimer;
    private final Timer swingTimer;

    private final JLabel titleLabel;
    private final JLabel timerLabel;
    private final JLabel statusLabel;
    private final JLabel statsLabel;
    private final ProgressRing progressRing;

    private final JButton startButton;
    private final JButton pauseResumeButton;
    private final JButton resetButton;
    private final JButton skipButton;

    private final JSpinner workMinutesSpinner;
    private final JSpinner shortBreakMinutesSpinner;
    private final JSpinner longBreakMinutesSpinner;
    private final JSpinner longBreakEverySpinner;
    private final JCheckBox soundCheckBox;

    public MainFrame() {
        super("番茄钟 Fanqie");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(860, 520));
        getContentPane().setBackground(UiTheme.BG);

        PomodoroConfig initialConfig = PomodoroConfig.defaultConfig();
        pomodoroTimer = new PomodoroTimer(initialConfig);

        titleLabel = new JLabel("番茄钟", SwingConstants.CENTER);
        titleLabel.setFont(UiTheme.TITLE_FONT);
        titleLabel.setForeground(UiTheme.TEXT);

        timerLabel = new JLabel("25:00", SwingConstants.CENTER);
        timerLabel.setFont(UiTheme.TIMER_FONT);
        timerLabel.setForeground(UiTheme.TEXT);

        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(UiTheme.NORMAL_FONT);
        statusLabel.setForeground(UiTheme.MUTED_TEXT);

        statsLabel = new JLabel("已完成番茄：0", SwingConstants.CENTER);
        statsLabel.setFont(UiTheme.NORMAL_FONT);
        statsLabel.setForeground(UiTheme.MUTED_TEXT);

        progressRing = new ProgressRing();
        progressRing.setPreferredSize(new Dimension(240, 240));

        startButton = new JButton("开始");
        pauseResumeButton = new JButton("暂停");
        resetButton = new JButton("重置");
        skipButton = new JButton("跳过");

        JPanel left = buildLeftPanel();

        workMinutesSpinner = createMinutesSpinner(initialConfig.getWorkSeconds() / 60);
        shortBreakMinutesSpinner = createMinutesSpinner(initialConfig.getShortBreakSeconds() / 60);
        longBreakMinutesSpinner = createMinutesSpinner(initialConfig.getLongBreakSeconds() / 60);
        longBreakEverySpinner = new JSpinner(new SpinnerNumberModel(initialConfig.getLongBreakEveryWorkSessions(), 1, 12, 1));
        soundCheckBox = new JCheckBox("结束提示音", true);
        soundCheckBox.setBackground(UiTheme.PANEL);

        JPanel right = buildSettingsPanel();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.72);
        split.setBorder(null);
        split.setDividerSize(1);
        split.setBackground(UiTheme.BG);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(split, BorderLayout.CENTER);

        // Important: register listener after all UI components are initialized.
        pomodoroTimer.addListener(this);

        swingTimer = new Timer(UI_TICK_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pomodoroTimer.tick();
            }
        });
        swingTimer.start();

        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UiTheme.BG);
        panel.setBorder(new EmptyBorder(18, 18, 18, 12));

        JPanel card = new JPanel();
        card.setBackground(UiTheme.PANEL);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                new EmptyBorder(18, 18, 18, 18)
        ));

        titleLabel.setAlignmentX(CENTER_ALIGNMENT);
        timerLabel.setAlignmentX(CENTER_ALIGNMENT);
        statusLabel.setAlignmentX(CENTER_ALIGNMENT);
        statsLabel.setAlignmentX(CENTER_ALIGNMENT);
        progressRing.setAlignmentX(CENTER_ALIGNMENT);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(10));
        card.add(progressRing);
        card.add(Box.createVerticalStrut(14));
        card.add(timerLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(statusLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(statsLabel);
        card.add(Box.createVerticalStrut(16));
        card.add(buildButtonsPanel());

        panel.add(card, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildButtonsPanel() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setBackground(UiTheme.PANEL);

        startButton.setPreferredSize(new Dimension(90, 34));
        pauseResumeButton.setPreferredSize(new Dimension(90, 34));
        resetButton.setPreferredSize(new Dimension(90, 34));
        skipButton.setPreferredSize(new Dimension(90, 34));

        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pomodoroTimer.start();
            }
        });
        pauseResumeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pomodoroTimer.getStatus() == TimerStatus.RUNNING) {
                    pomodoroTimer.pause();
                } else if (pomodoroTimer.getStatus() == TimerStatus.PAUSED) {
                    pomodoroTimer.resume();
                }
            }
        });
        resetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pomodoroTimer.reset();
            }
        });
        skipButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pomodoroTimer.skipToNextSession();
            }
        });

        buttons.add(startButton);
        buttons.add(pauseResumeButton);
        buttons.add(resetButton);
        buttons.add(skipButton);
        return buttons;
    }

    private JPanel buildSettingsPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(UiTheme.BG);
        wrapper.setBorder(new EmptyBorder(18, 12, 18, 18));

        JPanel card = new JPanel();
        card.setBackground(UiTheme.PANEL);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                new EmptyBorder(18, 18, 18, 18)
        ));

        JLabel header = new JLabel("设置", SwingConstants.LEFT);
        header.setFont(UiTheme.TITLE_FONT.deriveFont(22f));
        header.setForeground(UiTheme.TEXT);
        header.setAlignmentX(LEFT_ALIGNMENT);

        card.add(header);
        card.add(Box.createVerticalStrut(12));
        card.add(settingRow("专注（分钟）", workMinutesSpinner));
        card.add(Box.createVerticalStrut(10));
        card.add(settingRow("短休息（分钟）", shortBreakMinutesSpinner));
        card.add(Box.createVerticalStrut(10));
        card.add(settingRow("长休息（分钟）", longBreakMinutesSpinner));
        card.add(Box.createVerticalStrut(10));
        card.add(settingRow("长休息间隔（番茄）", longBreakEverySpinner));
        card.add(Box.createVerticalStrut(12));

        soundCheckBox.setAlignmentX(LEFT_ALIGNMENT);
        card.add(soundCheckBox);
        card.add(Box.createVerticalStrut(14));

        JButton applyButton = new JButton("应用设置");
        applyButton.setAlignmentX(LEFT_ALIGNMENT);
        applyButton.setPreferredSize(new Dimension(120, 34));
        applyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyConfigFromUi();
            }
        });
        card.add(applyButton);

        card.add(Box.createVerticalStrut(18));
        JLabel tips = new JLabel("<html><body style='width:260px'>小提示：<br/>- 开始后可暂停/继续<br/>- 跳过用于快速切换到下一段<br/>- 仅在空闲状态下应用设置会自动回到专注起点</body></html>");
        tips.setFont(UiTheme.SMALL_FONT);
        tips.setForeground(UiTheme.MUTED_TEXT);
        tips.setAlignmentX(LEFT_ALIGNMENT);
        card.add(tips);

        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel settingRow(String name, JSpinner spinner) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(UiTheme.PANEL);
        row.setAlignmentX(LEFT_ALIGNMENT);

        JLabel label = new JLabel(name);
        label.setFont(UiTheme.NORMAL_FONT);
        label.setForeground(UiTheme.TEXT);

        spinner.setPreferredSize(new Dimension(90, 28));
        row.add(label, BorderLayout.WEST);
        row.add(spinner, BorderLayout.EAST);
        return row;
    }

    private void applyConfigFromUi() {
        int workMin = ((Number) workMinutesSpinner.getValue()).intValue();
        int shortMin = ((Number) shortBreakMinutesSpinner.getValue()).intValue();
        int longMin = ((Number) longBreakMinutesSpinner.getValue()).intValue();
        int every = ((Number) longBreakEverySpinner.getValue()).intValue();

        if (workMin <= 0 || shortMin <= 0 || longMin <= 0 || every <= 0) {
            JOptionPane.showMessageDialog(this, "所有设置都必须大于 0。", "设置错误", JOptionPane.WARNING_MESSAGE);
            return;
        }

        PomodoroConfig config = new PomodoroConfig(workMin * 60, shortMin * 60, longMin * 60, every);
        pomodoroTimer.updateConfig(config);
        if (pomodoroTimer.getStatus() == TimerStatus.IDLE) {
            pomodoroTimer.reset();
        }
    }

    private static JSpinner createMinutesSpinner(int value) {
        return new JSpinner(new SpinnerNumberModel(value, 1, 180, 1));
    }

    @Override
    public void onStatusChanged(TimerStatus status) {
        if (status == TimerStatus.IDLE) {
            startButton.setEnabled(true);
            pauseResumeButton.setEnabled(false);
            pauseResumeButton.setText("暂停");
            resetButton.setEnabled(false);
            skipButton.setEnabled(false);
        } else if (status == TimerStatus.RUNNING) {
            startButton.setEnabled(false);
            pauseResumeButton.setEnabled(true);
            pauseResumeButton.setText("暂停");
            resetButton.setEnabled(true);
            skipButton.setEnabled(true);
        } else {
            startButton.setEnabled(false);
            pauseResumeButton.setEnabled(true);
            pauseResumeButton.setText("继续");
            resetButton.setEnabled(true);
            skipButton.setEnabled(true);
        }
        refreshStatusHint();
    }

    @Override
    public void onSessionChanged(SessionType sessionType, int totalSeconds, int remainingSeconds, int completedWorkSessions) {
        titleLabel.setText(sessionType.getDisplayName());
        updateColors(sessionType);
        updateTimerText(remainingSeconds);
        updateProgress(totalSeconds, remainingSeconds);
        statsLabel.setText("已完成番茄：" + completedWorkSessions);
        refreshStatusHint();
    }

    @Override
    public void onTick(SessionType sessionType, int totalSeconds, int remainingSeconds, int completedWorkSessions) {
        updateTimerText(remainingSeconds);
        updateProgress(totalSeconds, remainingSeconds);
        statsLabel.setText("已完成番茄：" + completedWorkSessions);

        if (remainingSeconds == 0 && soundCheckBox.isSelected()) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    @Override
    public void onConfigChanged(PomodoroConfig config) {
        // No-op: UI reads spinners.
    }

    private void updateTimerText(int remainingSeconds) {
        timerLabel.setText(TimeText.formatSeconds(remainingSeconds));
    }

    private void updateProgress(int totalSeconds, int remainingSeconds) {
        double p;
        if (totalSeconds <= 0) {
            p = 0.0;
        } else {
            p = (double) (totalSeconds - remainingSeconds) / (double) totalSeconds;
        }
        progressRing.setProgress(p);
    }

    private void updateColors(SessionType type) {
        Color c;
        if (type == SessionType.WORK) {
            c = UiTheme.WORK;
        } else if (type == SessionType.SHORT_BREAK) {
            c = UiTheme.SHORT_BREAK;
        } else {
            c = UiTheme.LONG_BREAK;
        }
        progressRing.setColor(c);
        titleLabel.setForeground(c);
    }

    private void refreshStatusHint() {
        TimerStatus status = pomodoroTimer.getStatus();
        SessionType type = pomodoroTimer.getSessionType();

        if (status == TimerStatus.IDLE) {
            statusLabel.setText("空闲：点击「开始」进入专注");
            return;
        }
        if (status == TimerStatus.PAUSED) {
            statusLabel.setText("已暂停：随时继续");
            return;
        }

        if (type == SessionType.WORK) {
            statusLabel.setText("进行中：保持专注");
        } else {
            statusLabel.setText("休息中：放松一下");
        }
    }
}
