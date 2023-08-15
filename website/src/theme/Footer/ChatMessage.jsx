import React from 'react';
import bot from './bot.png';
import user from './user.png';
import ReactMarkdown from 'react-markdown'
import hljs from "highlight.js";
import { useEffect } from 'react';
import "highlight.js/styles/github.css";

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
      <div className="chat-message user-message" id={props.id} >
        <div className="flex items-end">
          <div className="flex flex-col space-y-2 text-base mx-2 order-2 items-start">
            <div>
              <span className=
                "px-4 py-2 rounded-lg inline-block rounded-bl-none bg-gray-300 text-gray-600">
                <ReactMarkdown>{props.text}</ReactMarkdown>
              </span>
            </div>
          </div>
          <img src={user} alt="User Profile" className="w-6 h-6 rounded-full order-1" />
        </div>
      </div>
  } else if (props.userType === "bot") {
    chatMessage =
      <div className="w-[30em] chat-message bot-message" id={props.id}>
        <div className="">
          <div className="flex items-end">
            <div className="flex flex-col space-y-2 text-base mx-2 order-2 items-start">
              <div className="flex flex-col">
                <div className="w-full px-4 py-2 space-y-2 rounded-t-lg inline-block bg-blue-600 text-white">
                  <ReactMarkdown>{props.text}</ReactMarkdown>
                </div>

                {props.references !== undefined && props.references.length > 0 ?
                  <div className="flex flex-wrap text-xs w-full px-4 py-2 space-y-2 rounded-b-lg rounded-br-none bg-blue-500 text-white">
                    <p className='text-sm'>You can discover more information by checking out the pages mentioned below:</p>
                    <ol className="flex flex-wrap list-inside">
                      {
                        props.references.map((item, index) => (
                          <li key={index} className="mr-2">
                            <a className="underline text-white text-sm" href={item.url}>{item.title}</a>
                          </li>
                        ))

                      }</ol>
                  </div> : null
                }
              </div>
            </div>
            <img src={bot} alt="Robot Profile" className="w-6 h-6 rounded-full order-1" />
          </div>
        </div>
      </div>
  }

  return (chatMessage)
}

export default ChatMessage
