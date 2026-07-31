import { mount } from 'svelte'
import App from './App.svelte'
import './styles.css'

const initialTheme = localStorage.getItem('bridgemods.theme') ?? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
document.documentElement.dataset.theme = initialTheme
document.querySelector('meta[name="theme-color"]')?.setAttribute('content', initialTheme === 'dark' ? '#11140f' : '#f7f9f2')

const app = mount(App, { target: document.getElementById('root')! })

export default app
