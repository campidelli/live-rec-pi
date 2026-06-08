package campidelli.liverecpi.recording.adapters.outbound;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import campidelli.liverecpi.recording.domain.model.StartedRecording;
import campidelli.liverecpi.recording.domain.model.SupportedFormat;
import campidelli.liverecpi.recording.ports.inbound.StartRecordingCommand;
import campidelli.liverecpi.recording.ports.outbound.StartRecordingPort;
import campidelli.liverecpi.recording.ports.outbound.StopRecordingPort;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class JavaSoundSessionRecorderAdapter implements StartRecordingPort, StopRecordingPort {

    private static final Logger log = LoggerFactory.getLogger(JavaSoundSessionRecorderAdapter.class);
    private static final int SAMPLE_RATE = 44100;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, RecordingSession> activeSessions = new ConcurrentHashMap<>();

    @Override
    public StartedRecording startRecording(StartRecordingCommand command) {
        validate(command);

        String audioSourceId = command.audioSourceId();
        if (activeSessions.containsKey(audioSourceId)) {
            throw new IllegalStateException("Recording is already active for audio source: " + audioSourceId);
        }

        Mixer mixer = findMixer(audioSourceId);
        if (mixer == null) {
            throw new IllegalArgumentException("Audio source not found: " + audioSourceId);
        }

        int inputChannelCount = command.tracks().stream()
            .mapToInt(track -> track.audioSourceIndex())
            .max()
            .orElseThrow();
        int availableInputChannels = getAvailableInputChannels(mixer);
        if (availableInputChannels <= 0) {
            throw new IllegalArgumentException("Audio source has no available input channels: " + audioSourceId);
        }
        if (inputChannelCount > availableInputChannels) {
            throw new IllegalArgumentException(
                    "Requested input channel " + inputChannelCount
                            + " but audio source only provides " + availableInputChannels + " channels: " + audioSourceId);
        }

        Path outputDir = Path.of(command.outputDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(outputDir);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to create output directory: " + outputDir, exception);
        }

        AudioFormat interleavedFormat = new AudioFormat(SAMPLE_RATE, BITS_PER_SAMPLE, inputChannelCount, true, false);
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, interleavedFormat);
        if (!AudioSystem.isLineSupported(lineInfo)) {
            throw new IllegalArgumentException("The requested format is not supported for audio source: " + audioSourceId);
        }

        RecordingSession session = new RecordingSession();
        activeSessions.put(audioSourceId, session);
        executor.submit(() -> record(command, mixer, interleavedFormat, session, outputDir));

        return new StartedRecording(audioSourceId, outputDir.toString(), command.format(), command.tracks().size());
    }

    @Override
    public boolean stopRecording(String audioSourceId) {
        RecordingSession session = activeSessions.get(audioSourceId);
        if (session == null) {
            return false;
        }

        session.stop();
        return true;
    }

    private void validate(StartRecordingCommand command) {
        if (command.format() != SupportedFormat.WAV) {
            throw new IllegalArgumentException("Only WAV recording is currently supported");
        }
        if (command.tracks().isEmpty()) {
            throw new IllegalArgumentException("At least one track is required");
        }
        boolean allIndexesPositive = command.tracks().stream().allMatch(track -> track.audioSourceIndex() > 0);
        if (!allIndexesPositive) {
            throw new IllegalArgumentException("audioSourceIndex must be one or greater");
        }
    }

    private void record(
            StartRecordingCommand command,
            Mixer mixer,
            AudioFormat interleavedFormat,
            RecordingSession session,
            Path outputDir) {
        int trackCount = command.tracks().size();
        int inputChannelCount = interleavedFormat.getChannels();
        int frameSize = inputChannelCount * BYTES_PER_SAMPLE;

        try (TargetDataLine targetLine = (TargetDataLine) mixer.getLine(new DataLine.Info(TargetDataLine.class, interleavedFormat))) {
            session.bind(targetLine);
            targetLine.open(interleavedFormat);
            targetLine.start();

            FileOutputStream[] trackOutputs = new FileOutputStream[trackCount];
            int[] trackByteCounts = new int[trackCount];
            File[] trackFiles = new File[trackCount];

            try {
                for (int index = 0; index < trackCount; index++) {
                    String fileName = sanitizeFileName(command.tracks().get(index).name()) + ".wav";
                    trackFiles[index] = outputDir.resolve(fileName).toFile();
                    trackOutputs[index] = new FileOutputStream(trackFiles[index]);
                    trackOutputs[index].write(new byte[44]);
                }

                byte[] interleavedBuffer = new byte[frameSize * 1000];
                while (!session.stopped()) {
                    int bytesRead = targetLine.read(interleavedBuffer, 0, interleavedBuffer.length);
                    if (bytesRead <= 0) {
                        continue;
                    }

                    for (int offset = 0; offset < bytesRead; offset += frameSize) {
                        for (int trackIndex = 0; trackIndex < trackCount; trackIndex++) {
                            int sourceChannelIndex = command.tracks().get(trackIndex).audioSourceIndex() - 1;
                            int sampleIndex = offset + (sourceChannelIndex * BYTES_PER_SAMPLE);
                            trackOutputs[trackIndex].write(interleavedBuffer, sampleIndex, BYTES_PER_SAMPLE);
                            trackByteCounts[trackIndex] += BYTES_PER_SAMPLE;
                        }
                    }
                }
            } finally {
                finalizeTracks(trackOutputs, trackFiles, trackByteCounts);
            }
        } catch (Exception exception) {
            log.warn("Recording loop failed for audio source {}", command.audioSourceId(), exception);
        } finally {
            activeSessions.remove(command.audioSourceId());
        }
    }

    private Mixer findMixer(String audioSourceId) {
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (info.getName().equalsIgnoreCase(audioSourceId) && !info.getDescription().contains("Port")) {
                return AudioSystem.getMixer(info);
            }
        }
        return null;
    }

    private int getAvailableInputChannels(Mixer mixer) {
        int maxChannels = java.util.Arrays.stream(mixer.getTargetLineInfo())
                .mapToInt(JavaSoundSessionRecorderAdapter::maxChannels)
                .max()
                .orElse(0);

        return normalizeChannels(maxChannels);
    }

    private static int maxChannels(Line.Info lineInfo) {
        if (!(lineInfo instanceof DataLine.Info dataLineInfo)) {
            return 0;
        }

        int maxChannels = 0;
        for (AudioFormat format : dataLineInfo.getFormats()) {
            int channels = format.getChannels();
            if (channels > maxChannels) {
                maxChannels = channels;
            }
        }
        return maxChannels;
    }

    private static int normalizeChannels(int channels) {
        return channels == AudioSystem.NOT_SPECIFIED ? 0 : channels;
    }

    private void finalizeTracks(FileOutputStream[] trackOutputs, File[] trackFiles, int[] trackByteCounts) {
        for (int index = 0; index < trackOutputs.length; index++) {
            try {
                if (trackOutputs[index] != null) {
                    trackOutputs[index].close();
                }
                if (trackFiles[index] != null) {
                    fixWavHeader(trackFiles[index], trackByteCounts[index]);
                }
            } catch (Exception exception) {
                log.warn("Failed to finalize recording track {}", index + 1, exception);
            }
        }
    }

    private void fixWavHeader(File file, int totalAudioBytes) throws IOException {
        int totalDataLen = totalAudioBytes + 36;
        byte[] header = new byte[44];
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put("RIFF".getBytes());
        buffer.putInt(totalDataLen);
        buffer.put("WAVE".getBytes());
        buffer.put("fmt ".getBytes());
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(SAMPLE_RATE);
        buffer.putInt(SAMPLE_RATE * BYTES_PER_SAMPLE);
        buffer.putShort((short) BYTES_PER_SAMPLE);
        buffer.putShort((short) BITS_PER_SAMPLE);
        buffer.put("data".getBytes());
        buffer.putInt(totalAudioBytes);

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
            randomAccessFile.seek(0);
            randomAccessFile.write(header);
        }
    }

    private String sanitizeFileName(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    @PreDestroy
    void shutdown() {
        activeSessions.values().forEach(RecordingSession::stop);
        executor.shutdownNow();
    }

    private static final class RecordingSession {
        private volatile boolean stopped;
        private volatile TargetDataLine targetDataLine;

        private RecordingSession() {
        }

        private void bind(TargetDataLine targetDataLine) {
            this.targetDataLine = targetDataLine;
        }

        private boolean stopped() {
            return stopped;
        }

        private void stop() {
            this.stopped = true;
            if (targetDataLine != null) {
                targetDataLine.stop();
                targetDataLine.close();
            }
        }
    }
}
