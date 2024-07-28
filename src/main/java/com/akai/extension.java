package com.akai;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.callback.ShortMidiMessageReceivedCallback;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Transport;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.RemoteControl;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.ControllerExtension;

public class extension extends ControllerExtension {
   private Transport mTransport;
   private ControllerHost host;

   private Integer[] mutePadsArray = { 1, 4, 7, 10, 13, 16, 19, 22 };
   private Integer[] recPadsArray = { 3, 6, 9, 12, 15, 18, 21, 24 };
   private Integer BANK_RIGHT = 25;
   private Integer BANK_LEFT = 26;

   private int selectedBank = 0;
   private Track[] tracks = new Track[8];
   private CursorRemoteControlsPage[] remoteControlPages = new CursorRemoteControlsPage[8];

   private int[][] knobCCs = {
         { 16, 17, 18 },
         { 20, 21, 22 },
         { 24, 25, 26 },
         { 28, 29, 30 },
         { 46, 47, 48 },
         { 50, 51, 52 },
         { 54, 55, 56 },
         { 58, 59, 60 }
   };

   protected extension(final extensionDefinition definition, final ControllerHost host) {
      super(definition, host);
   }

   @Override
   public void init() {
      this.host = getHost();
      mTransport = host.createTransport();
      host.getMidiInPort(0).setMidiCallback((ShortMidiMessageReceivedCallback) msg -> onMidi0(msg));
      host.getMidiInPort(0).setSysexCallback((String data) -> onSysex0(data));

      for (int i = 0; i < tracks.length; i++) {
         tracks[i] = host.createTrackBank(8, 0, 0).getItemAt(i);
         remoteControlPages[i] = tracks[i].createCursorRemoteControlsPage(24); // Example: 24 remote controls
      }

      initPads(1);
      updateLed(BANK_LEFT, 0);
      updateLed(BANK_RIGHT, 0);

      handleRecPadPress(recPadsArray[0]);

      host.showPopupNotification("Akai Midimix Initialized");
   }

   @Override
   public void exit() {
      initPads(0);
      updateLed(BANK_LEFT, 0);
      updateLed(BANK_RIGHT, 0);
      host.showPopupNotification("AkaiMidimix Exited");
   }

   private void initPads(Integer value) {
      for (Integer i : mutePadsArray) {
         updateLed(i, value);
      }
      for (Integer i : recPadsArray) {
         updateLed(i, 0);
      }
   }

   @Override
   public void flush() {
   }

   private void onMidi0(ShortMidiMessage msg) {
      int data1 = msg.getData1();
      int data2 = msg.getData2();
      int status = msg.getStatusByte();

      if (status == 0x90 && data2 > 0) {
         handleRecPadPress(data1);
      } else if (status == 0xB0) {
         handleKnobTurn(data1, data2);
      }
   }

   private void handleRecPadPress(int pad) {
      for (int i = 0; i < recPadsArray.length; i++) {
         if (recPadsArray[i] == pad) {
            updateLed(recPadsArray[selectedBank], 0);
            selectedBank = i;
            updateLed(recPadsArray[selectedBank], 1);
            host.showPopupNotification("Bank " + (selectedBank + 1) + " selected");
            updateBank();
            break;
         }
      }
   }

   private void updateBank() {
      for (int trackIndex = 0; trackIndex < remoteControlPages.length; trackIndex++) {
         CursorRemoteControlsPage page = remoteControlPages[trackIndex];

         for (int j = 0; j < page.getParameterCount(); j++) {
            page.getParameter(j).setIndication(false);
         }

         for (int knobIndex = 0; knobIndex < 3; knobIndex++) {
            int parameterIndex = selectedBank * 3 + knobIndex;

            int pageIndex = parameterIndex / 8;
            int indexInPage = parameterIndex % 8;

            page.selectedPageIndex().set(pageIndex);

            if (indexInPage < page.getParameterCount()) {
               page.getParameter(indexInPage).setIndication(true);
            }
         }
      }
      host.showPopupNotification("Remote controls updated to bank " + (selectedBank + 1));
   }

   private void handleKnobTurn(int cc, int value) {
      for (int trackIndex = 0; trackIndex < knobCCs.length; trackIndex++) {
         for (int knobIndex = 0; knobIndex < knobCCs[trackIndex].length; knobIndex++) {
            if (knobCCs[trackIndex][knobIndex] == cc) {
               int parameterIndex = selectedBank * 3 + knobIndex;

               int pageIndex = parameterIndex / 8;
               int indexInPage = parameterIndex % 8;

               CursorRemoteControlsPage page = remoteControlPages[trackIndex];
               page.selectedPageIndex().set(pageIndex);

               if (indexInPage < page.getParameterCount()) {
                  RemoteControl control = page.getParameter(indexInPage);
                  control.set(value, 128);
               }
            }
         }
      }
   }

   private void onSysex0(final String data) {
      if (data.equals("f07f7f0605f7"))
         mTransport.rewind();
      else if (data.equals("f07f7f0604f7"))
         mTransport.fastForward();
      else if (data.equals("f07f7f0601f7"))
         mTransport.stop();
      else if (data.equals("f07f7f0602f7"))
         mTransport.play();
      else if (data.equals("f07f7f0606f7"))
         mTransport.record();
   }

   private void updateLed(int id, int color) {
      host.getMidiOutPort(0).sendMidi(0x90, id, color);
   }
}
