package no.uit.metapipe.cpouta;

import java.time.Duration;
import java.util.Calendar;

public class Stopwatch
{

    private long start;
    private long stop;
    private String format;

    Stopwatch()
    {
        reset();
        format = "%02d:%02d:%02d";
    }

    void start()
    {
        start = Calendar.getInstance().getTimeInMillis();
    }

    boolean isStarted()
    {
        return start > 0;
    }

    void stop()
    {
        if(isStarted())
        {
            stop = Calendar.getInstance().getTimeInMillis();
        }
        else
        {
            System.out.println("Stopwatch is not started yet!");
        }
    }

    void reset()
    {
        start = 0;
        stop = 0;
    }

    String stopGetResultReset()
    {
        String res = "";
        if(isStarted())
        {
            stop();
            res = getResultString();
            reset();
        }
        else
        {
            System.out.println("Stopwatch is not started yet!");
        }
        return res;
    }

    void setFormat(String f)
    {
        format = f;
    }

    String getResultString() {
        long seconds = Duration.ofMillis(stop - start).getSeconds();
        long absSeconds = Math.abs(seconds);
        String positive = String.format(
                format,
                absSeconds / 3600,
                (absSeconds % 3600) / 60,
                absSeconds % 60);
        return seconds < 0 ? "-" + positive : positive;
    }

}
