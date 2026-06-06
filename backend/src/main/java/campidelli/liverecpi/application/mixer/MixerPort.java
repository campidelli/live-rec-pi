package campidelli.liverecpi.application.mixer;

import java.util.Optional;

import campidelli.liverecpi.domain.mixer.Mixer;
import campidelli.liverecpi.domain.mixer.OnlineDevice;
import campidelli.liverecpi.domain.mixer.ProbeSpecification;

public interface MixerPort {
  String getDefaultModelKey();

  String getDefaultName();
  
  ProbeSpecification getProbeSpecification();

  Optional<Mixer> supports(OnlineDevice device);

  Mixer connect(Mixer mixer);
}
