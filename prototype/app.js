const examples = {
  en: [
    {
      title: 'Find your way<br>back to wonder.',
      copy: 'Somewhere between the first light of morning and the last train home, the world feels a little bigger.',
      translatedTitle: 'Encontre o caminho<br>de volta ao encanto.',
      translatedCopy: 'Entre a primeira luz da manhã e o último trem para casa, o mundo parece um pouco maior.'
    },
    {
      title: 'Take the long<br>way around.',
      copy: 'The best discoveries rarely happen when you are in a hurry. Leave a little room for the unexpected.',
      translatedTitle: 'Escolha o caminho<br>mais demorado.',
      translatedCopy: 'As melhores descobertas raramente acontecem com pressa. Deixe espaço para o inesperado.'
    },
    {
      title: 'A table for<br>the whole story.',
      copy: 'Good food brings strangers together, even when the menu is written in a language you are still learning.',
      translatedTitle: 'Uma mesa para<br>a história inteira.',
      translatedCopy: 'A boa comida aproxima desconhecidos, mesmo quando o cardápio está num idioma que você ainda aprende.'
    }
  ],
  es: [
    {
      title: 'Encuentra el camino<br>de vuelta al asombro.',
      copy: 'Entre la primera luz de la mañana y el último tren a casa, el mundo parece un poco más grande.',
      translatedTitle: 'Encontre o caminho<br>de volta ao encanto.',
      translatedCopy: 'Entre a primeira luz da manhã e o último trem para casa, o mundo parece um pouco maior.'
    },
    {
      title: 'Elige el camino<br>más largo.',
      copy: 'Los mejores descubrimientos rara vez ocurren con prisa. Deja un poco de espacio para lo inesperado.',
      translatedTitle: 'Escolha o caminho<br>mais demorado.',
      translatedCopy: 'As melhores descobertas raramente acontecem com pressa. Deixe espaço para o inesperado.'
    },
    {
      title: 'Una mesa para<br>toda la historia.',
      copy: 'La buena comida acerca a desconocidos, incluso cuando el menú está escrito en un idioma que aún estás aprendiendo.',
      translatedTitle: 'Uma mesa para<br>a história inteira.',
      translatedCopy: 'A boa comida aproxima desconhecidos, mesmo quando o cardápio está num idioma que você ainda aprende.'
    }
  ]
};

const sourceLanguage = document.querySelector('#source-language');
const title = document.querySelector('#sample-title');
const copy = document.querySelector('#sample-copy');
const translatedTitle = document.querySelector('#translated-title');
const translatedCopy = document.querySelector('#translated-copy');
const overlay = document.querySelector('#translation-overlay');
const status = document.querySelector('#session-status');
const activity = document.querySelector('#activity-label');
const previewLabel = document.querySelector('#preview-label');
const liveDot = document.querySelector('#live-dot');
const startButton = document.querySelector('#start-session');
const stopButton = document.querySelector('#stop-session');
const permissionDialog = document.querySelector('#permission-dialog');
const settingsDialog = document.querySelector('#settings-dialog');
const toast = document.querySelector('#toast');

let currentExample = 0;
let active = false;
let selectedMode = 'balanced';
let toastTimer;

function showToast(message) {
  toast.textContent = message;
  toast.classList.add('visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('visible'), 2700);
}

function renderExample() {
  const example = examples[sourceLanguage.value][currentExample % examples[sourceLanguage.value].length];
  title.innerHTML = example.title;
  copy.textContent = example.copy;
  translatedTitle.innerHTML = example.translatedTitle;
  translatedCopy.textContent = example.translatedCopy;
  overlay.classList.toggle('hidden', !active);
  if (active) activity.textContent = 'Texto detectado · tradução atualizada';
}

function setActive(value) {
  active = value;
  overlay.classList.toggle('hidden', !active);
  status.classList.toggle('active', active);
  status.querySelector('span').textContent = active ? 'Traduzindo' : 'Pronto';
  liveDot.classList.toggle('active', active);
  previewLabel.textContent = active ? 'SESSÃO ATIVA' : 'PRÉVIA DO CONTEÚDO';
  activity.textContent = active ? 'Texto detectado · tradução atualizada' : 'Aguardando início da sessão';
  startButton.classList.toggle('hidden', active);
  stopButton.classList.toggle('hidden', !active);
}

startButton.addEventListener('click', () => permissionDialog.showModal());
document.querySelector('#confirm-permission').addEventListener('click', () => {
  setActive(true);
  setTimeout(() => showToast('Sessão de demonstração iniciada. A captura real ainda não está conectada.'), 150);
});
stopButton.addEventListener('click', () => {
  setActive(false);
  showToast('Sessão encerrada. O conteúdo temporário foi removido da prévia.');
});

document.querySelector('#next-sample').addEventListener('click', () => {
  currentExample = (currentExample + 1) % examples[sourceLanguage.value].length;
  renderExample();
});
sourceLanguage.addEventListener('change', () => {
  currentExample = 0;
  renderExample();
});

document.querySelectorAll('.mode-option').forEach(button => {
  button.addEventListener('click', () => {
    document.querySelectorAll('.mode-option').forEach(option => option.classList.remove('selected'));
    button.classList.add('selected');
    selectedMode = button.dataset.mode;
    const labels = { economy: 'Econômico', balanced: 'Equilibrado', quality: 'Qualidade' };
    showToast(`Modo ${labels[selectedMode]} selecionado para a demonstração.`);
  });
});

document.querySelector('#open-settings').addEventListener('click', () => settingsDialog.showModal());
document.querySelector('#text-size').addEventListener('input', event => {
  document.querySelector('.translation-overlay').style.setProperty('--overlay-font-size', `${event.target.value}px`);
  document.querySelector('.translation-overlay > div').style.fontSize = `${event.target.value}px`;
});
document.querySelector('#opacity').addEventListener('input', event => {
  document.querySelector('.translation-overlay').style.background = `rgba(255,255,250,${event.target.value / 100})`;
});
document.querySelector('#mode-help').addEventListener('click', () => showToast('Os modos representam diferentes equilíbrios entre rapidez, qualidade e uso de serviços remotos.'));

permissionDialog.addEventListener('close', () => {
  if (!active && permissionDialog.returnValue === 'cancel') showToast('Tudo bem. A tradução não foi iniciada.');
});

renderExample();
