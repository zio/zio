import React, { useEffect, useRef } from 'react';
import ChatPrompt from "./ChatPrompt";


const useClickOutside = (ref, callback) => {
  const handleClick = (event) => {
    if (event.target.id === "chat-button") return null;
    if (ref.current && !ref.current.contains(event.target)) {
      callback();
    }
  };

  useEffect(() => {
    document.addEventListener('click', handleClick);

    return () => {
      document.removeEventListener('click', handleClick);
    };
  });
};


export default function PopupChatWindow({ visible, onClose }) {

  const ref = useRef();

  useClickOutside(ref, onClose);

  const welcome_messages = [
    "🌟 Greetings from ZIO Chat! 🌟 Have questions about ZIO? Look no further! I'm here to be your ZIO guru. Whether you're a seasoned pro or just starting out, I've got answers tailored just for you. Fire away with your ZIO queries, and I'll hit you back with the best insights I've got. Let's dive into the world of ZIO together!",
    "🔥 Ignite your ZIO journey with ZIO Chat! 🔥 Ready to unravel the intricacies of ZIO? As your trusty sidekick, I'm here to light the way. No matter if you're wandering through functional forests or traversing effectful landscapes, I've got answers to keep you on track. Your ZIO adventure starts here – ask me anything and let's explore together!",
    "Hello! 😊 I'm your ZIO Chat Bot, designed to be your go-to resource for all things ZIO! Whether you're new to this powerful functional effect system or a seasoned pro, I'm here to assist you. ZIO empowers you to build concurrent and resilient applications in Scala, offering referential transparency, composable data types, type-safety, and more. So, go ahead and ask me anything about ZIO – I'm here to provide the answers you need! 🤖",
    "Hi there! 👋 I'm ZIO Chat, your friendly neighborhood ZIO expert. I'm here to help you navigate the world of ZIO. Whether you're a seasoned pro or just starting out, fire away with your ZIO questions. I will hit you back with the best insights I've got. Let's dive into the world of ZIO together!",
    "🎉 Hey there, ZIO aficionado! 🎉 Step into the ZIO wonderland with me as your guide. Whether you're a functional fanatic or just dipping your toes into the ZIO ecosystem, I'm here to make your journey smooth. Got ZIO questions? I've got answers! Let's navigate the ZIO universe together and unlock its infinite possibilities!",
    "Hi, fellow coder! 🌟 Welcome to your ZIO adventure with the ZIO Chat Bot. 🚀 Let's master functional Scala together. From referential transparency to concurrent prowess, I've got your back. Ready to dive in? Your questions, my insights – let's conquer ZIO! 💡🤖",
  ];

  return (
    <div
      ref={ref}
      id="container"
      className={`fixed 2xl:w-[30%] xl:w-[40%] lg:w-[50%] md:w-[100%] sm:w-[100%] bottom-0 right-0 ${visible ? 'block' : 'hidden'} `}>
      <ChatPrompt
        title="ZIO Chat"
        defaultQuestion="Write a question about ZIO!"
        websocketHost="chat.zio.dev"
        websocketPort="80"
        websocketEndpoint="/chat"
        welcomeMessages={welcome_messages}
        fullScreen={false}
      />
    </div>
  );
}
