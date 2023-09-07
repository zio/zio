import hljs from "highlight.js";
import "highlight.js/styles/github.css";
import React, { useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import bot from './bot.png';
import user from './user.png';

function ChatMessage(props) {
  useEffect(() => {
    if (props.highlight && props.userType === "bot") {
      document
        .querySelectorAll("#" + props.id + " pre code")
        .forEach((el) => {
          hljs.highlightElement(el);
        });
    }
  });

  let chatMessage;
  if (props.userType === "user") {
    chatMessage =
      <div className="w-full chat-message user-message text-base flex flex-row space-x-2" id={props.id} >
        <img src={user} alt="User Profile" className="w-6 h-6 rounded-full" />
        <ReactMarkdown className='px-4 py-2 rounded-lg rounded-bl-none bg-gray-300 text-gray-600'>{props.text}</ReactMarkdown>
      </div>
  } else if (props.userType === "bot") {
    chatMessage =
      <div className="w-full chat-message bot-message text-base flex flex-row space-x-2" id={props.id}>
        <img src={bot} alt="Robot Profile" className="w-6 h-6 rounded-full" />
        <div className="w-full">
          <ReactMarkdown className='px-4 py-2 space-y-2 rounded-t-lg bg-blue-600 text-white'>{props.text}</ReactMarkdown>
          <References items={props.references} />
        </div>
      </div>
  }

  return (chatMessage)
}

export default ChatMessage

function References(props) {
  return (
    <> {props.items !== undefined && props.items.length > 0 ? (
      <div className="flex flex-wrap text-xs px-4 py-2 rounded-b-lg rounded-br-none bg-blue-500 text-white">
        <p className='text-sm'>You can discover more information by checking out the pages mentioned below:</p>
        <ol className="flex flex-wrap list-inside">
          {
            props.items.map((item, index) => (
              <li key={index} className="mr-2">
                <a className="underline text-white text-sm" href={item.page_url}>{item.page_title}</a>
              </li>
            ))
          }</ol>
      </div>) : null}
    </>
  )
}
