package campidelli.liverecpi;

import javax.sound.sampled.*;

public class AudioInputDiscoverer {

  public static void main(String[] args) {
    System.out.println("Searching for available audio input channels...\n");

    // 1. Get info about all audio mixers installed on the system
    Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();

    for (Mixer.Info info : mixerInfos) {
      Mixer mixer = AudioSystem.getMixer(info);

      // 2. We specifically look for "TargetDataLine" types (Audio Inputs)
      Line.Info[] targetLineInfos = mixer.getTargetLineInfo();

      // If this mixer has input lines, print its details
      if (targetLineInfos.length > 0) {
        System.out.println("Device Name: " + info.getName());
        System.out.println("Description: " + info.getDescription());

        for (Line.Info lineInfo : targetLineInfos) {
          if (lineInfo instanceof DataLine.Info) {
            DataLine.Info dataLineInfo = (DataLine.Info) lineInfo;

            // 3. Inspect the audio formats this input device supports
            AudioFormat[] formats = dataLineInfo.getFormats();
            int maxChannels = 0;

            for (AudioFormat format : formats) {
              if (format.getChannels() > maxChannels) {
                maxChannels = format.getChannels();
              }
            }

            // 4. Report the maximum channels (e.g., 1 for Mono, 2 for Stereo)
            if (maxChannels == AudioSystem.NOT_SPECIFIED) {
              System.out.println("  -> Channels: Dynamic / Not Specified");
            } else {
              System.out.println("  -> Max Supported Channels: " + maxChannels);
            }
          }
        }
        System.out.println("--------------------------------------------");
      }
    }
  }
}
