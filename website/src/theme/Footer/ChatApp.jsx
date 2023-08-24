import PopupChatWindow from './PopupChatWindow';
import React, { useState } from 'react';

export const ChatApp = (props) => {
  const [isOpen, setIsOpen] = useState(false)
  const close = () => setIsOpen(false)
  const open = () => setIsOpen(true)

  return (
    <>
      <button
        id='chat-button'
        className='fixed border-0 font-sans font-medium bg-orange-400 rounded-tl-md text-black px-4 py-2 hover:scale-95 transition text-lg bottom-0 right-0'
        onClick={open}
      >
        ZIO Chat!
      </button>
      <PopupChatWindow onClose={close} visible={isOpen} />
    </>
  );
};


export default ChatApp;
