import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import "./styles.css";

class AppErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  render() {
    if (this.state.error) {
      return (
        <main className="loading-shell">
          <div className="brand">
            <div>
              <div className="wordmark">
                <span className="brand-mark">S</span>
                <h1>SpeedLink</h1>
              </div>
              <p>App failed to load. Refresh the page after the latest deploy.</p>
              <small>{this.state.error.message}</small>
            </div>
          </div>
        </main>
      );
    }

    return this.props.children;
  }
}

createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <AppErrorBoundary>
      <App />
    </AppErrorBoundary>
  </React.StrictMode>,
);
