import React from "react";
import Footer from "@theme-original/Footer";
import { ChatApp } from "@bytebrain.ai/bytebrain-ui";

export default function FooterWrapper(props) {
  return (
    <>
      <Footer {...props} />
      <ChatApp
        websocketHost="chat.zio.dev"
        websocketPort="80"
        websocketEndpoint="/chat"
      />
    </>
  );
}
