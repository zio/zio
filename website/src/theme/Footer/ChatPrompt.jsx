import React from 'react';
import ChatMessage from './ChatMessage';
import './ChatPrompt.css'
import { useEffect } from 'react';

export const ChatPrompt = (props) => {

  function getRandom(arr) {
    const randomIndex = Math.floor(Math.random() * arr.length);
    return arr.splice(randomIndex, 1)[0];
  }

  const {
    title = 'ByteBrain ChatBot',
    defaultQuestion = 'Send your message',
    websocketHost,
    websocketPort,
    websocketEndpoint,
    welcomeMessages,
    fullScreen,
  } = props

  const greetings = welcomeMessages.map(m => ({
    userType: "bot",
    message: m,
    completed: true
  }));

  const [question, setQuestion] = React.useState('');
  const [messages, setMessages] = React.useState([getRandom(greetings)]);

  const handleChange = (event) => {
    setQuestion(event.target.value)
  }

  const handleSubmit = (event) => {
    event.preventDefault();
    handleClick();
    event.target.reset();
  }

  function isButtonDisabled() {
    if (messages.length === 0) { return false }
    else if (messages[messages.length - 1].userType == "user") { return true }
    else if (messages[messages.length - 1].userType == "bot") {
      return !messages[messages.length - 1].completed ? true : false
    }
  }

  const handleClick = () => {
    const host = websocketHost || window.location.hostname;
    const port = websocketPort === "80" ? "" : ":" + websocketPort;
    const protocol = websocketPort === "80" ? "wss://" : "ws://";
    const ws = new WebSocket(protocol + host + port + websocketEndpoint);
    ws.onopen = (event) => {
      setMessages(messages => messages.concat([
        {
          userType: "user",
          message: question,
          completed: true
        }
      ]));
      const history = messages.map(m => m.message).slice(1);
      const data = {
        "question": question,
        "history": history
      }
      ws.send(JSON.stringify(data));
    };
    ws.onmessage = function (event) {
      const result = JSON.parse(event.data)
      const word = result.token;
      const references = result.references;
      const completed = result.completed;
      setMessages(messages => {
        if (
          messages.length === 0 ||
          messages[messages.length - 1].userType === "user"
        ) {
          return [...messages, { userType: "bot", message: word, completed: completed }];
        } else {
          const updatedMessages = [...messages];
          updatedMessages[updatedMessages.length - 1] = {
            ...updatedMessages[updatedMessages.length - 1],
            message: updatedMessages[updatedMessages.length - 1].message + word,
            references: references,
            completed: completed
          };
          return updatedMessages;
        }
      });
    };
  }

  return (
    <div id='ChatPrompt' className={`justify-between flex flex-col p-5 rounded-md bg-gray-100 max-h-screen ${fullScreen ? "h-screen" : ""}`}>
      <h1 className="text-orange-600 text-3xl font-bold mb-6">{title}</h1>
      <div
        id="scroller"
        className="flex flex-col space-y-4 overflow-y-scroll scroll-smooth">
        {
          messages.map((m, id) =>
            <ChatMessage
              key={id} 
              id={"chat" + id} 
              userType={m.userType} 
              text={m.message} 
              references={m.references} 
              highlight={m.completed} />
          )
        }
        <div id="anchor"></div>
      </div>
      <form onSubmit={handleSubmit} className="flex bottom-5 left-5 right-5 h-10 mt-4">
        <input
          type="text"
          id="promptInput"
          onChange={handleChange}
          className="w-full px-4 py-2 border-0 rounded-md text-base text-black bg-gray-200 placeholder-gray-500 focus:outline-none font-sans font-normal"
          placeholder={defaultQuestion}
        />
        <button disabled={isButtonDisabled()}
          id="generateBtn"
          type='submit'
          className="px-6 rounded-md border-0 bg-black text-base text-white hover:bg-gray-900 focus:outline-none disabled:opacity-75 disabled:cursor-not-allowed font-sans font-normal"
        >
          Send
        </button>
      </form>
    </div>
  );
};

export default ChatPrompt
