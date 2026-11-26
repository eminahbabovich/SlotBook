// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { useRef } from 'react'
import { afterEach, describe, expect, test } from 'vitest'
import { useReflektor } from './reflektor.js'

function Stranica() {
	const ref = useRef(null)
	useReflektor(ref)
	return (
		<div ref={ref}>
			<div className="kartica" data-testid="kartica">
				<p>Lista čekanja</p>
			</div>
			<p data-testid="van">Česta pitanja</p>
		</div>
	)
}

function postaviOkvir(element, left, top) {
	element.getBoundingClientRect = () => ({ left, top, right: left + 300, bottom: top + 200, width: 300, height: 200 })
}

afterEach(cleanup)

describe('reflektor na karticama', () => {
	test('kartica ispod miša dobije položaj miša u odnosu na svoj ugao', () => {
		render(<Stranica />)
		const kartica = screen.getByTestId('kartica')
		postaviOkvir(kartica, 100, 40)

		fireEvent.pointerMove(screen.getByText('Lista čekanja'), { clientX: 160, clientY: 90, pointerType: 'mouse' })

		expect(kartica.style.getPropertyValue('--mis-x')).toBe('60px')
		expect(kartica.style.getPropertyValue('--mis-y')).toBe('50px')
	})

	test('dodir prstom ne pali reflektor', () => {
		render(<Stranica />)
		const kartica = screen.getByTestId('kartica')
		postaviOkvir(kartica, 0, 0)

		fireEvent.pointerMove(kartica, { clientX: 10, clientY: 10, pointerType: 'touch' })

		expect(kartica.style.getPropertyValue('--mis-x')).toBe('')
	})

	test('miš van kartice ne mijenja nijednu karticu', () => {
		render(<Stranica />)
		const kartica = screen.getByTestId('kartica')

		fireEvent.pointerMove(screen.getByTestId('van'), { clientX: 10, clientY: 10, pointerType: 'mouse' })

		expect(kartica.style.getPropertyValue('--mis-x')).toBe('')
	})
})
