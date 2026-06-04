package campidelli.liverecpi.application.mixer;

import java.util.Collection;
import java.util.List;

import campidelli.liverecpi.domain.mixer.OnlineDevice;
import campidelli.liverecpi.domain.mixer.ProbeSpecification;

public interface DeviceDiscoveryPort {
    List<OnlineDevice> discoverOnlineDevices(Collection<ProbeSpecification> specs);
}