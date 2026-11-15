import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'

const korijen = document.getElementById('root')
if (korijen === null) {
	throw new Error('index.html nema element #root')
}

createRoot(korijen).render(
	<StrictMode>
		<App />
	</StrictMode>,
)
