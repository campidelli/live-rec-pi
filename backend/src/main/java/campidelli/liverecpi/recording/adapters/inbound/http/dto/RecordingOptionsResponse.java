package campidelli.liverecpi.recording.adapters.inbound.http.dto;

import java.util.List;

import campidelli.liverecpi.recording.domain.model.RecordingOptions;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class RecordingOptionsResponse {

	private final List<SupportedFormatResponse> supportedFormats;
	private final String sessionNamePrefix;
	private final String outputDir;

	public RecordingOptionsResponse(
			List<SupportedFormatResponse> supportedFormats,
			String sessionNamePrefix,
			String outputDir) {
		this.supportedFormats = supportedFormats;
		this.sessionNamePrefix = sessionNamePrefix;
		this.outputDir = outputDir;
	}

	public List<SupportedFormatResponse> getSupportedFormats() {
		return supportedFormats;
	}

	public String getSessionNamePrefix() {
		return sessionNamePrefix;
	}

	public String getOutputDir() {
		return outputDir;
	}

	public static RecordingOptionsResponse fromDomain(RecordingOptions recordingOptions) {
		List<SupportedFormatResponse> supportedFormats = recordingOptions.supportedFormats().stream()
				.map(format -> new SupportedFormatResponse(format.getExtension(), format.getDescription()))
				.toList();

		return new RecordingOptionsResponse(
				supportedFormats,
				recordingOptions.sessionNamePrefix(),
				recordingOptions.outputDir());
	}

	@Serdeable
	public static class SupportedFormatResponse {

		private final String extension;
		private final String description;

		public SupportedFormatResponse(String extension, String description) {
			this.extension = extension;
			this.description = description;
		}

		public String getExtension() {
			return extension;
		}

		public String getDescription() {
			return description;
		}
	}
}
