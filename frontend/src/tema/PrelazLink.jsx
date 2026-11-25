import { Link, useNavigate } from 'react-router'
import { uzPrelaz } from './prelaz.js'

// Link koji promjenu ekrana radi kao View Transition (tema/prelaz.js): stari ekran ode, novi dođe,
// a element s istim view-transition-name na oba ekrana (naziv resursa) preleti. React Router to sam
// radi samo uz data router, a mi imamo BrowserRouter. Ctrl, Shift i srednji klik ostaju browseru
// (novi tab), a bez podrške u browseru ovo je običan Link.
export function PrelazLink({ to, onClick, ...ostalo }) {
	const navigate = useNavigate()

	function klik(e) {
		onClick?.(e)
		if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
		e.preventDefault()
		uzPrelaz(() => navigate(to), { vrsta: 'ekran' })
	}

	return <Link to={to} onClick={klik} {...ostalo} />
}
