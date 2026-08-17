import DefaultTheme from 'vitepress/theme'
import OryxTerminal from './components/OryxTerminal.vue'
import OryxProcess from './components/OryxProcess.vue'
import './style.css'

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component('OryxTerminal', OryxTerminal)
    app.component('OryxProcess', OryxProcess)
  },
}
