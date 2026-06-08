package campidelli.liverecpi.recording.adapters.outbound;

import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;

import campidelli.liverecpi.recording.domain.model.AudioSource;
import campidelli.liverecpi.recording.ports.outbound.GetAudioSourcesPort;
import campidelli.liverecpi.recording.ports.outbound.GetAvailableInputChannelsPort;
import jakarta.inject.Singleton;

@Singleton
public class JavaSoundAudioSourcesAdapter implements GetAvailableInputChannelsPort, GetAudioSourcesPort {

    @Override
    public int getAvailableInputChannels(String mixerType) {
        return getAudioSources(mixerType).stream()
                .mapToInt(AudioSource::availableInputChannels)
                .sum();
    }

    @Override
    public List<AudioSource> getAudioSources(String mixerType) {
        return java.util.Arrays.stream(AudioSystem.getMixerInfo())
                .filter(info -> matchesMixerType(info, mixerType))
                .map(JavaSoundAudioSourcesAdapter::toAudioSource)
                .filter(audioSource -> audioSource.availableInputChannels() > 0)
                .toList();
    }

    private static boolean matchesMixerType(Mixer.Info info, String mixerType) {
        return info.getName().equalsIgnoreCase(mixerType)
                && !info.getDescription().contains("Port");
    }

    private static AudioSource toAudioSource(Mixer.Info info) {
        Mixer mixer = AudioSystem.getMixer(info);
        int availableInputChannels = java.util.Arrays.stream(mixer.getTargetLineInfo())
                .mapToInt(JavaSoundAudioSourcesAdapter::maxChannels)
                .max()
                .orElse(0);

        return new AudioSource(
                info.getName(),
                info.getName(),
                info.getDescription(),
                normalizeChannels(availableInputChannels));
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
}
