import { Component, type ErrorInfo, type ReactNode } from 'react'
import { ErrorState } from '../state'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  error: Error | null
}

/**
 * Catches a render error anywhere below it and shows the shared error
 * state with a retry affordance instead of letting React unmount the
 * whole tree into a white page. Retry resets the caught error and
 * re-renders the children; if the same error recurs, it is caught again.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unexpected render error caught by ErrorBoundary', error, info)
  }

  private handleRetry = () => {
    this.setState({ error: null })
  }

  render() {
    if (this.state.error !== null) {
      return <ErrorState message={this.state.error.message} onRetry={this.handleRetry} />
    }
    return this.props.children
  }
}
