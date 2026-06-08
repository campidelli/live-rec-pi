package campidelli.liverecpi.recording.adapters.outbound;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import campidelli.liverecpi.recording.domain.model.RecordingOptions;
import campidelli.liverecpi.recording.domain.model.SupportedFormat;
import campidelli.liverecpi.recording.ports.outbound.GetRecordingOptionsPort;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StaticRecordingOptionsAdapter implements GetRecordingOptionsPort {

  private static final Logger log = LoggerFactory.getLogger(StaticRecordingOptionsAdapter.class);
  private static final DateTimeFormatter SESSION_PREFIX_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

  @Override
  public RecordingOptions getRecordingOptions() {
    String sessionNamePrefix = LocalDate.now().format(SESSION_PREFIX_DATE_FORMAT) + "-";
    Path outputDir = Path.of(System.getProperty("user.home"), "recordings");

    try {
      Files.createDirectories(outputDir);
    } catch (IOException exception) {
      log.warn("Failed to create recordings directory at {}", outputDir, exception);
    }

    return new RecordingOptions(
        List.of(SupportedFormat.values()),
        sessionNamePrefix,
        outputDir.toAbsolutePath().normalize().toString());
  }
}
