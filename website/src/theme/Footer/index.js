import React from 'react';
import Footer from '@theme-original/Footer';
import { ChatApp } from '@bytebrain.ai/bytebrain-ui';

export default function FooterWrapper(props) {
  return (
    <>
      <Footer {...props} />
      <div className="zio-chat">
        <ChatApp
          websocketHost="chat.zio.dev"
          websocketPort="80"
          websocketEndpoint="/chat"
          apikey="91088b1e-9b7f-4c0f-baf9-0800799209c5"
          askOnSelect
        />
      </div>
    </>
  );
}
