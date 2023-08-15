import React from 'react';
import Footer from '@theme-original/Footer';
import ChatApp from './ChatApp';

export default function FooterWrapper(props) {
  return (
    <>
      <Footer {...props} />
      <ChatApp />
    </>
  );
}
