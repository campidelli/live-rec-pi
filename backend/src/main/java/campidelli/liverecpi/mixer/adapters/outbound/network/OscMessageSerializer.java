package campidelli.liverecpi.mixer.adapters.outbound.network;

import java.nio.ByteBuffer;

import com.illposed.osc.ByteArrayListBytesReceiver;
import com.illposed.osc.OSCPacket;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializerAndParserBuilder;

import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OscMessageSerializer {

  private static final Logger log = LoggerFactory.getLogger(OscMessageSerializer.class);

  public byte[] serialize(OSCMessage message) {
    try {
      OSCSerializerAndParserBuilder builder = new OSCSerializerAndParserBuilder();
      ByteArrayListBytesReceiver bytesReceiver = new ByteArrayListBytesReceiver();
      builder.buildSerializer(bytesReceiver).write(message);
      return bytesReceiver.toByteArray();
    } catch (Exception e) {
      log.error("OSC serialization failed. path={}", message.getAddress(), e);
      throw new RuntimeException("OSC Serialization failure", e);
    }
  }

  public OSCMessage parse(byte[] packetBytes, int length) {
    try {
      ByteBuffer responseBuffer = ByteBuffer.wrap(packetBytes, 0, length);
      OSCSerializerAndParserBuilder builder = new OSCSerializerAndParserBuilder();
      OSCPacket packet = builder.buildParser().convert(responseBuffer);
      if (packet instanceof OSCMessage message) {
        return message;
      }
      return null;
    } catch (Exception e) {
      log.error("OSC parsing failed.", e);
      throw new RuntimeException("OSC parsing failure", e);
    }
  }
}