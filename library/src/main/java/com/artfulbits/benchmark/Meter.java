package com.artfulbits.benchmark;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Performance measurement class. Should be used for micro-benchmarking and
 * comparison of different implementations. Class implements very simple logic.
 */
@SuppressLint("DefaultLocale")
public final class Meter {
    /* [ CONSTANTS ] ============================================================================================= */

  /** Flag. Tells Meter class that loop is with unknown number of iterations. */
  public static final int LOOP_ENDLESS = -1000;
  /** Default path used for trace DUMPs. */
  public static final String DEFAULT_TRACE_PATH_PREFIX = Environment.getExternalStorageDirectory().getPath() + "/";

  /** preallocate size for reduce performance impacts. */
  private static final int PREALLOCATE = 256;
  /** length of the delimiter line. */
  private static final int DELIMITER_LENGTH = 80;
  /** Delimiter for statistics output. */
  private static final String DELIMITER = new String(new char[DELIMITER_LENGTH]).replace("\0", "-");
  /** One day in nanos. */
  private static final long ONEDAY_NANOS = 24 /* hours */ * 60 /* minutes */ * 60 /* sec */ *
          1000 /* millis */ * 1000 /* micros */ * 1000 /* nanos */;

  /** Bits cleanup mask. */
  private static final long MASK = 0xffffffffL;

	/* [ STATIC MEMBERS ] ========================================================================================== */

  private final static WeakHashMap<Thread, Meter> sThreads = new WeakHashMap<Thread, Meter>();

	/* [ MEMBERS ] ================================================================================================= */

  /** Current active measure. */
  private Measure mCurrent;
  /** List of captured measures. */
  private final List<Measure> mMeasures = new ArrayList<Measure>(PREALLOCATE);
  /** Instance of the meter class configuration. */
  private final Config mConfig = new Config();
  /** Calibrate metrics. */
  private final Calibrate mCalibrate = new Calibrate();

	/* [ OPTIONS ] ================================================================================================= */

  public Config getConfig() {
    return mConfig;
  }

	/* [ STATIC METHODS ] ========================================================================================== */

  /**
   * Calibrate class, benchmark cost of execution for Meter class on a specific device. Allows to compute more
   * accurate results during statistics displaying.
   */
  public void calibrate() {
    // DONE: measure each method execution time and store for future calculations

    long start = timestamp(), point;

    start();
    mCalibrate.Start = (point = timestamp()) - start;

    beat();
    mCalibrate.Beat = (start = timestamp()) - point;

    log("calibrate");
    mCalibrate.Log = (point = timestamp()) - start;

    skip();
    mCalibrate.Skip = (start = timestamp()) - point;

    loop();
    mCalibrate.Loop = (point = timestamp()) - start;

    recap();
    mCalibrate.Recap = (start = timestamp()) - point;

    unloop();
    mCalibrate.UnLoop = (point = timestamp()) - start;

    end();
    mCalibrate.End = (start = timestamp()) - point;

    pop();
    mCalibrate.Pop = timestamp() - start;
  }

  /** Method used for timestamp value extracting. */
  @SuppressLint("NewApi")
  private final long timestamp() {
    final boolean apiLevel = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1);

    if (apiLevel && !getConfig().UseSystemNanos) {
      // alternative:
      return SystemClock.elapsedRealtimeNanos();
    }

    return System.nanoTime();
  }

  /**
   * Check is meter tracking something now.
   *
   * @return <code>true</code> - we are in tracking mode, otherwise <code>false</code>.
   */
  public boolean isTracking() {
    return (mCurrent != null);
  }

	/* ============================================================================================ */

  /**
   * Start benchmarking. On each call a new benchmark measurement object created.
   *
   * @return unique id of the benchmark object.
   */
  private synchronized int start() {
    mMeasures.add(mCurrent = new Measure(this));

    if (getConfig().DoMethodsTrace) {
      Debug.startMethodTracing(getConfig().MethodsTraceFilePath);
    }

    return mCurrent.Id;
  }

  /**
   * Start benchmarking with attached custom log message.
   *
   * @param log log message.
   * @return unique id of the benchmark object.
   */
  public int start(final String log) {
    int id = start();
    mCurrent.Logs.append(mCurrent.Position.get() - 1, log);
    return id;
  }

  /** Add/Include step beat interval into benchmarking report. */
  private void beat() {
    mCurrent.add(timestamp(), Bits.INCLUDE);
  }

  /**
   * Add/Include step beat interval into benchmarking report with custom log message.
   *
   * @param log log message.
   */
  public void beat(final String log) {
    beat();
    log(log);
  }

  /**
   * Assign a log string to a last step in benchmark run.
   *
   * @param log message to assign.
   */
  public void log(final String log) {
    mCurrent.Logs.append(mCurrent.Position.get() - 1, log);
  }

  /** Skip/Ignore/Exclude step interval from benchmarking. */
  public void skip() {
    mCurrent.add(timestamp(), Bits.EXCLUDE);
  }

  /**
   * Skip/Ignore/Exclude step interval from benchmarking with custom log message.
   *
   * @param log log message.
   */
  public void skip(final String log) {
    skip();
    log(log);
  }

  /** Start the loop tracking with unknown number of iterations. */
  public void loop() {
    loop(LOOP_ENDLESS);
  }

  /**
   * Start the loop tracking with unknown number of iterations with custom log message.
   *
   * @param log log message.
   */
  public void loop(final String log) {
    loop(LOOP_ENDLESS, log);
  }

  /**
   * Start the loop tracking.
   *
   * @param counter maximum number of iterations.
   */
  public void loop(final int counter) {
    final long time = timestamp();
    final long flags = Bits.INCLUDE | Bits.LOOP | Math.abs(counter);
    final long modifiers = (counter < 0) ? Bits.ENDLESS : 0;

    mCurrent.add(time, flags | modifiers);
  }

  /**
   * Start the loop tracking.
   *
   * @param counter maximum number of iterations.
   * @param log log message.
   */
  public void loop(final int counter, final String log) {
    loop(counter);
    log(log);
  }

  /** Inside the loop store one iteration time. */
  public void recap() {
    mCurrent.add(timestamp(), Bits.INCLUDE | Bits.RECAP);
  }

  /**
   * Inside the loop store one iteration time.
   *
   * @param log log message.
   */
  public void recap(final String log) {
    recap();
    log(log);
  }

  /** Loop ends. Finalize benchmarking of the loop. */
  public void unloop() {
    mCurrent.add(timestamp(), Bits.UNLOOP);
  }

  /**
   * Loop ends. Finalize benchmarking of the loop.
   *
   * @param log log message.
   */
  public void unloop(final String log) {
    unloop();
    log(log);
  }

  /** End benchmarking, print statistics, prepare class for next run. */
  public void finish() {
    end();
    stats();
    pop();
  }

  /** End benchmarking. */
  public void end() {
    mCurrent.add(timestamp(), Bits.END);

    if (getConfig().DoMethodsTrace) {
      Debug.stopMethodTracing();
    }
  }

  /** Print captured statistics into logcat. */
  public void stats() {
    final Config config = getConfig();
    final int totalSteps = mCurrent.Position.get();
    final List<Step> steps = new ArrayList<Step>(totalSteps);
    long totalSkipped = 0;

    for (int i = 0; i < totalSteps; i++) {
      final Step subStep = new Step(config, mCurrent, i);

      steps.add(subStep);

      totalSkipped += subStep.Skipped;
    }

    // dump all
    for (int i = 0, len = steps.size(); i < len; i++) {
      if ((mCurrent.Flags[i] & Bits.EXCLUDE) == Bits.EXCLUDE) {
        Log.w(config.OutputTag, steps.get(i).toString());
      } else {
        Log.v(config.OutputTag, steps.get(i).toString());
      }
    }

    // generate summary of tracking: top items by time, total time, total skipped time,
    if (getConfig().ShowSummary) {
      Log.v(config.OutputTag, DELIMITER);

      // TODO: generate summary of tracking: top items by time, total time, total skipped time,
      Log.i(config.OutputTag, String.format(Locale.US, "final: %.3f ms%s, steps: %d",
              toMillis(mCurrent.total() - totalSkipped),
              (totalSkipped > 1000) ? String.format(" (-%.3f ms)", toMillis(totalSkipped)) : "",
              totalSteps));
    }

    final PriorityQueue<Step> pq = new PriorityQueue<Step>(totalSteps, Step.Comparator);
    pq.addAll(steps);

    // publish longest steps
    if (config.ShowTopNLongest > 0) {
      Log.v(config.OutputTag, DELIMITER);

      for (int i = 1, len = Math.min(pq.size(), config.ShowTopNLongest); i <= len; i++) {
        final Step step = pq.poll();

        if (!step.IsSkipped) {
          Log.i(config.OutputTag, "top-" + i + ": " + step.toString());
        }
      }
    }

    Log.v(config.OutputTag, DELIMITER);
  }

  /**
   * Utility method. Converts array of long primitive types to collection of objects.
   *
   * @param timing array of long values.
   * @return Collection with converted values.
   */
  public static List<Object> toParams(long[] timing) {
    final List<Object> params = new ArrayList<Object>(Math.max(timing.length, PREALLOCATE));

    for (int j = 0, len = timing.length; j < len; j++) {
      params.add(timing[j]);
    }

    return params;
  }

  /**
   * Calculate percent value.
   *
   * @param value current value.
   * @param min x-scale start point.
   * @param max y-scale end point.
   * @return calculated percent value.
   */
  public static double percent(final long value, final long min, final long max) {
    final long point = value - min;
    final long end = max - min;

    return (point * 100.0 /* percentage scale */) / end;
  }

  /**
   * Convert nanoseconds to milliseconds with high accuracy.
   *
   * @param nanos nanoseconds to convert.
   * @return total milliseconds.
   */
  public static double toMillis(final long nanos) {
    return nanos / 1000.0 /* micros in 1 milli */ / 1000.0 /* nanos in 1 micro */;
  }

	/* ============================================================================================ */

  /** Remove from measurements stack last done tracking. Method switches current Measure instance to next in stack. */
  public synchronized void pop() {
    mMeasures.remove(mCurrent.Id);
    mCurrent = (mMeasures.size() == 0) ? null : mMeasures.get(mMeasures.size() - 1);
  }

  /**
   * End benchmarking, print statistics, prepare class for next run.
   *
   * @param log log message.
   */
  public void finish(final String log) {
    end(log);
    stats();
    pop();
  }

  /**
   * End benchmarking.
   *
   * @param log log message.
   */
  public void end(final String log) {
    end();
    log(log);
  }

  /** Cleanup the class. */
  public void clear() {
    mMeasures.clear();
  }

	/* [ CONSTRUCTORS ] ============================================================================================ */

  /** Hidden constructor. */
  private Meter() {
    // do nothing, just keep the protocol of calls safe
  }

  /** Get instance of Meter class for current thread. */
  public static Meter getInstance() {
    final Thread key = Thread.currentThread();

    if (!sThreads.containsKey(key)) {
      sThreads.put(key, new Meter());
    }

    return sThreads.get(key);
  }

	/* [ NESTED DECLARATIONS ] ===================================================================================== */

  /**
   * Flags that we use for identifying measurement steps. First part of the long value is used
   * for custom data attaching, like: size of the array, index in array, etc.
   * Please do not use first 32 bits for any state flags.
   * Note: all fields declared in interface by default become "public final static".
   */
  private interface Bits {
    /** Time stamp included into statistics. */
    long INCLUDE = 0x000100000000L;
    /** Time stamp excluded into statistics. */
    long EXCLUDE = 0x000200000000L;
    /** Time stamp of Loop point creation. */
    long LOOP = 0x000400000000L;
    /** Time stamp of exiting from loop. */
    long UNLOOP = 0x000800000000L;
    /** Time stamp of loop iteration. */
    long RECAP = 0x001000000000L;
    /** The loop is endless. */
    long ENDLESS = 0x002000000000L;
    /** Time stamp start of statistics collecting. */
    long START = 0x100000000000L;
    /** Time stamp ends of statistics collecting. */
    long END = 0x200000000000L;
  }

  /** Statistics output and Tracking behavior configuration. */
  public final static class Config {
    /** Output tag for logs used by meter class. */
    public String OutputTag = "meter";
    /**
     * Default DUMP trace file name. Used only when {@link Config#DoMethodsTrace} is set to <code>true</code>. Field
     * initialized by android default dump file name.
     */
    public String MethodsTraceFilePath = DEFAULT_TRACE_PATH_PREFIX + "dmtrace.trace";
    /**
     * <code>true</code> - in addition do Android default methods tracing, otherwise <code>false</code>. {@link
     * Config#MethodsTraceFilePath} defines the output file name for trace info.
     */
    public boolean DoMethodsTrace;
    /** <code>true</code> - show steps grid in output, otherwise <code>false</code>. */
    public boolean ShowStepsGrid;
    /** <code>true</code> - show accumulated time column, otherwise <code>false</code>. */
    public boolean ShowAccumulatedTime;
    /** <code>true</code> - show cost in percents column, otherwise <code>false</code>. */
    public boolean ShowStepCostPercents = true;
    /** <code>true</code> - show step cost time column, otherwise <code>false</code>. */
    public boolean ShowStepCostTime = true;
    /** <code>true</code> - show log message column, otherwise <code>false</code>. */
    public boolean ShowLogMessage = true;
    /** <code>true</code> - show after tracking summary, otherwise <code>false</code>. */
    public boolean ShowSummary = true;
    /** <code>true</code> - place column starter symbol "| " on each row start, otherwise <code>false</code>. */
    public boolean ShowTableStart = true;
    /** Show in statistics summary list of longest steps. Define the Number of steps to show. */
    public int ShowTopNLongest = 5;
    /** True - use {@link System#nanoTime()}, otherwise use {@link SystemClock#elapsedRealtimeNanos()}. */
    public boolean UseSystemNanos = true;
  }

  /** Calibration results holder. */
  public final static class Calibrate {
    public long Start;
    public long Beat;
    public long Log;
    public long Skip;
    public long Loop;
    public long Recap;
    public long UnLoop;
    public long End;
    public long Pop;

    @Override
    public String toString() {
      return String.format(Locale.US,
              "Calibrate [St/Be/Lg/Sk/Lo/Re/Un/En/Po]: %.3f/%.3f/%.3f/%.3f/%.3f/%.3f/%.3f/%.3f/%.3f/%.3f/%.3f ms",
              toMillis(Start), toMillis(Beat), toMillis(Log), toMillis(Skip),
              toMillis(Loop), toMillis(Recap), toMillis(UnLoop), toMillis(End), toMillis(Pop));
    }
  }

  /** Internal class for storing measurement statistics. */
  private final static class Measure {
    /** Unique identifier of the measurement instance. */
    public final int Id;
    /** Unique identifier of the thread which instantiate the class. */
    @SuppressWarnings("unused")
    public final long ThreadId;
    /** The start time of tracking. */
    public final long Start;
    /** Stored timestamp of each benchmarking call. */
    public final long[] Ranges = new long[PREALLOCATE];
    /** Stored flags for each corresponding timestamp in {@link #Ranges}. */
    public final long[] Flags = new long[PREALLOCATE];
    /** Current position in the benchmarking array {@link #Ranges}. */
    public final AtomicInteger Position = new AtomicInteger();
    /** Stack of loop's executed during benchmarking. */
    public final Queue<Integer> LoopsQueue = new ArrayDeque<Integer>(PREALLOCATE);
    /** Step index - to - Loop. */
    public final SparseArray<Loop> Loops = new SparseArray<Loop>();
    /** Step index - to - Log message. */
    public final SparseArray<String> Logs = new SparseArray<String>(PREALLOCATE);
    /** Reference on parent class instance. */
    public final Meter Parent;

		/* [ CONSTRUCTOR ] ============================================================================================ */

    public Measure(final Meter meter) {
      Parent = meter;
      Id = Parent.mMeasures.size();
      ThreadId = Thread.currentThread().getId();
      Start = Parent.timestamp();

      add(Start, Bits.INCLUDE | Bits.START);
    }

    /** Get the last timestamp (maximum) of the measurement. */
    public long theEnd() {
      final int totalTimes = Position.get();
      final long end = Ranges[totalTimes - 1];

      return end;
    }

    /** Get total time of measurement. */
    public long total() {
      return theEnd() - Start;
    }

    public int add(final long time, final long flags) {
      final boolean isIteration = (flags & Bits.RECAP) == Bits.RECAP;
      final boolean isLoopStart = (flags & Bits.LOOP) == Bits.LOOP;
      final boolean isLoopEnd = (flags & Bits.UNLOOP) == Bits.UNLOOP;

      final int index;

      if (isLoopStart) {
        final int counter = (int) (flags & MASK);
        final long onlyFlags = flags & (~MASK);
        index = addLoop(time, onlyFlags, counter);
      } else if (isLoopEnd) {
        // into first part of bits we store step index for easier loop begin identifying
        index = addStep(time, flags | LoopsQueue.poll());
      } else if (isIteration) {
        index = addIteration(time, flags);
      } else {
        index = addStep(time, flags);
      }

      return index;
    }

    private int addStep(final long time, final long flags) {
      final int index = Position.getAndIncrement();

      Ranges[index] = time;
      Flags[index] = flags;

      return index;
    }

    private int addIteration(final long time, final long flags) {
      final int index = Position.get();
      final int loop = LoopsQueue.peek();

      Loops.get(loop).add(time, flags);

      return index;
    }

    private int addLoop(final long time, final long flags, final int size) {
      final int index = addStep(time, flags);
      final boolean isEndless = (flags & Bits.ENDLESS) == Bits.ENDLESS;

      Loops.append(index, new Loop(time, (isEndless ? -1 : 1) * size));
      LoopsQueue.add(index);

      return index;
    }

    public String format() {
      final StringBuilder format = new StringBuilder(PREALLOCATE * 4);

      if (Parent.getConfig().ShowTableStart) {
        format.append("| ");
      }

      if (Parent.getConfig().ShowStepsGrid) {
        final String grid = new String(new char[Position.get()]).replace("\0", "%d | ");
        format.append(grid);
      }

      if (Parent.getConfig().ShowStepCostPercents) {
        format.append("%5.2f%% | ");
      }

      if (Parent.getConfig().ShowStepCostTime) {
        format.append("%8.3f ms | ");
      }

      if (Parent.getConfig().ShowAccumulatedTime) {
        format.append("%8.3f ms | ");
      }

      if (Parent.getConfig().ShowLogMessage) {
        format.append("%s");
      }

      return format.toString();
    }

    /**
     * Prepare log output part for a specific step.
     *
     * @param index - step position.
     * @return extracted log message for a step.
     */
    public String log(final int index) {
      final String log = Logs.get(index);

      final boolean isLoop = (Flags[index] & Bits.LOOP) == Bits.LOOP;
      final boolean isUnLoop = (Flags[index] & Bits.UNLOOP) == Bits.UNLOOP;
      final long custom = (Flags[index] & 0xffffffff);
      final String name = ((isLoop) ? "loop" : "step");

      // DONE: loop statistics should be displayed on the loop exit, not at the beginning
      final String prefix = (isUnLoop ? Loops.get((int) custom).stats() : "");
      final String body = (TextUtils.isEmpty(log) ? name + " #" + index : log);
      final String suffix = "";

      return (prefix + body + suffix);
    }
  }

  /** Loops iterations tracking. */
  private final static class Loop {
    /** Timestamp's of each iteration. */
    public final long[] Iterations;
    /** Flags storage for each time stamp. */
    public final long[] Flags;
    /** Index of first element in Iterations array. */
    public int Position;
    /** Quantity of stored iterations. */
    public int Counter;
    /** Total number of captured iterations. */
    public int TotalCaptured;
    /** <code>true</code> indicates endless loop tracking, otherwise number of iterations is known. */
    @SuppressWarnings("unused")
    public final boolean IsEndless;
    /** Start time of the loop statistics . */
    public final long Start;

    /**
     * Create class with preallocated space for timestamp's on each iteration.
     *
     * @param maxSize Number of expected iterations. If less than zero - class switch own mode to endless loops
     * tracking.
     */
    public Loop(final long time, final int maxSize) {
      final int size = Math.abs(maxSize);

      Start = time;
      IsEndless = (maxSize < 0);
      Iterations = new long[size];
      Flags = new long[size];
    }

    /**
     * Add time stamp of a new iteration.
     *
     * @param time time stamp.
     * @param flags time stamp flags.
     * @return index of iteration.
     */
    public int add(final long time, final long flags) {
      int index = Position;
      Iterations[index] = time;
      Flags[index] = flags;

      // cycled iteration pointer
      if (Iterations.length <= (++Position)) {
        Position = 0;
      }

      // if we in endless loop and overwriting old values
      if (Iterations.length < (++Counter)) {
        Counter = Iterations.length;
      }

      TotalCaptured++;

      return index;
    }

    /**
     * Calculate loop statistics.
     *
     * @return string with loop metrics.
     */
    public String stats() {
      final long loopStart = Start;
      final int endPoint = (Position - 1 < 0) ? Iterations.length - 1 : Position - 1;
      long loopTotal = Iterations[endPoint] - loopStart;

      long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
      long avg, total = 0, iteration, stepN = loopStart, stepM = loopStart;

      for (int i = 0; i < Counter; i++) {
        int index = toArrayIndex(i, Position, Counter, Iterations.length);

        stepM = Iterations[index];
        iteration = stepM - stepN;

        min = Math.min(min, iteration);
        max = Math.max(max, iteration);
        total += iteration;

        stepN = stepM;
      }

      // NOTE: http://en.wikipedia.org/wiki/Measurement_uncertainty
      avg = (total - min - max) / Math.max(1, Counter - 2);

      // normalize output for empty Loops. make number good looking for output
      if (Counter == 0) {
        avg = min = max = loopTotal = 0;
      }

      // "avg: %.3fms min: %.3fms max: %.3fms total:%.3fms calls:%d / "
      // "avg/min/max/total: %.3f/%.3f/%.3f/%.3f ms - calls:%d / "

      final String line = String.format(Locale.US, "avg/min/max/total: %.3f/%.3f/%.3f/%.3f ms - calls:%d / ",
              toMillis(avg), toMillis(min), toMillis(max), toMillis(loopTotal), TotalCaptured);

      return line;
    }

    /**
     * Convert range [0..counter] to position in cycled array.
     *
     * @param index index to convert.
     * @param position current cycled position in array.
     * @param count quantity of elements stored in array.
     * @param length length of the array.
     * @return converted index;
     */
    public static int toArrayIndex(final int index, final int position, final int count, final int length) {
      if (count > length) {
        throw new IllegalArgumentException();
      }

      // array is not filled and cycling is not enabled yet
      if (count < length) {
        return index;
      }
      // item is in a left range
      else if (index > length - position) {
        return index - (length - position);
      }
      // item is in a right range
      else {
        return position + index;
      }
    }
  }

  /** Statistics step. */
  private final static class Step {
    /** Compare steps by cost of execution. {@link #Total} */
    public final static Comparator<Step> Comparator = new Comparator<Step>() {
      /** {@inheritDoc} */
      @Override
      public int compare(final Step lhs, final Step rhs) {
        // output: left less right  == -1
        // output: left more right  == 1
        // output: left equal right == 0

        // first exclude "bad data" situations
        if (null == lhs || null == rhs)
          return (null == lhs) ? -1 : 1;

        // for skipped element shift there time to 0, that will make them last in list
        return -1 * Long.valueOf(lhs.Total - lhs.Skipped).compareTo(rhs.Total - rhs.Skipped);
      }
    };

    public final boolean IsSkipped;
    public final long Skipped;
    public final long Start;
    public final long Total;
    public final long AccumulatedTotal;
    public final double CostPercents;
    public final long[] Times;

    public final String Format;
    public final String Log;

    private final Config mConfig;

    public Step(final Config config, final Measure m, final int index) {
      mConfig = config;

      final long prevEndTime = m.Ranges[Math.max(0, index - 1)];

      Start = m.Ranges[index];

      // grid of steps
      Times = new long[m.Position.get()];
      Times[index] = Start;

      // calculate length of step
      Total = Start - prevEndTime;
      AccumulatedTotal = Start - m.Start;

      IsSkipped = ((m.Flags[index] & Bits.EXCLUDE) == Bits.EXCLUDE);
      Skipped = IsSkipped ? Total : 0;

      CostPercents = percent(Start, m.Start, m.theEnd()) - percent(prevEndTime, m.Start, m.theEnd());

      Format = m.format();
      Log = m.log(index);
    }

    public List<Object> toParams() {
      final List<Object> params = (mConfig.ShowStepsGrid) ?
              Meter.toParams(Times) :
              new ArrayList<Object>(PREALLOCATE);

      if (mConfig.ShowStepCostPercents) {
        params.add(CostPercents);
      }

      if (mConfig.ShowStepCostTime) {
        params.add(toMillis(Total));
      }

      if (mConfig.ShowAccumulatedTime) {
        params.add(toMillis(AccumulatedTotal));
      }

      if (mConfig.ShowLogMessage) {
        params.add(Log);
      }

      return params;
    }

    @Override
    public String toString() {
      return String.format(Locale.US, Format, toParams().toArray());
    }
  }
}