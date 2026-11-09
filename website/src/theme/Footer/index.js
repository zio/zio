import React, { useEffect, useRef } from 'react';
import Footer from '@theme-original/Footer';
import { ChatApp } from '@bytebrain.ai/bytebrain-ui';

// The chat window and its maximized view start right below the navbar. The
// navbar's height changes (the announcement bar pushes it down at scroll-top),
// so measure its bottom edge and store it in --bytebrain-offset-top.
function useNavbarOffset(wrapRef) {
  useEffect(() => {
    const wrap = wrapRef.current;
    const navbar = document.querySelector('.navbar');
    if (!wrap || !navbar) return undefined;
    let frame = 0;
    const update = () => {
      frame = 0;
      const bottom = Math.max(0, Math.round(navbar.getBoundingClientRect().bottom));
      wrap.style.setProperty('--bytebrain-offset-top', `${bottom}px`);
    };
    const schedule = () => {
      if (!frame) frame = window.requestAnimationFrame(update);
    };
    update();
    window.addEventListener('scroll', schedule, { passive: true });
    window.addEventListener('resize', schedule);
    return () => {
      window.removeEventListener('scroll', schedule);
      window.removeEventListener('resize', schedule);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, [wrapRef]);
}

export default function FooterWrapper(props) {
  const wrapRef = useRef(null);
  useNavbarOffset(wrapRef);
  return (
    <>
      <Footer {...props} />
      <div className="zio-chat" ref={wrapRef}>
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
