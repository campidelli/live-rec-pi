package campidelli.liverecpi.mixer.adapters.outbound.xr18;

import campidelli.liverecpi.mixer.domain.model.MixerDescriptor;
import campidelli.liverecpi.mixer.ports.outbound.GetMixerDescriptorPort;
import jakarta.inject.Singleton;

@Singleton
public class Xr18GetMixerDescriptorAdapter implements GetMixerDescriptorPort {

    @Override
    public MixerDescriptor getDescriptor() {
        // 16 channels + 1 stereo aux return channel (17-left and 18-right)
        return new MixerDescriptor("XR18", "Behringer", "X Air XR18", 17);
    }
}
