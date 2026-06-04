package campidelli.liverecpi.application.mixer;

import campidelli.liverecpi.domain.mixer.Mixer;
import campidelli.liverecpi.domain.mixer.ProbeSpecification;

public interface MixerPort {
  String getModelKey();
  String getDisplayName();
  ProbeSpecification getProbeSpecification();
  boolean supports(String modelSignature);
  Mixer connect(String ipAddress, int port);
}
