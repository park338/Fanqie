package cn.fanqie.pomodoro.ui;

final class TimeText {
    static String formatSeconds(int seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        int minutes = seconds / 60;
        int remain = seconds % 60;
        return pad2(minutes) + ":" + pad2(remain);
    }

    private static String pad2(int value) {
        if (value < 10) {
            return "0" + value;
        }
        return String.valueOf(value);
    }

    private TimeText() {
    }
}

