# Protótipo ScreenTranslator

Protótipo interativo de interface para iniciar a exploração do MVP. Abra `index.html` em um navegador moderno.

## O que já dá para experimentar

- Escolher idioma de origem (inglês ou espanhol) e alternar exemplos de conteúdo.
- Iniciar e encerrar uma sessão demonstrativa.
- Ver uma tradução sobreposta ao conteúdo de exemplo.
- Alternar entre modos Econômico, Equilibrado e Qualidade.
- Ajustar tamanho e transparência da faixa traduzida.
- Visualizar explicação de consentimento antes do início.

## Limites desta versão

Esta é uma prévia de interface em HTML/CSS/JavaScript, não um aplicativo Android nativo. A autorização de captura é simulada: não captura a tela, não usa OCR, não chama um serviço de tradução e não escuta áudio. As traduções são exemplos fixos. A área de áudio indica uma etapa futura. Não requer dependências ou chaves de API.

## Próximo incremento técnico

Criar o projeto Android nativo e substituir a autorização simulada pelo fluxo de consentimento do sistema. Depois, validar captura de tela e sobreposição em aparelho real, adicionar OCR local e conectar um provedor de tradução aprovado. O fluxo de áudio deve ser pesquisado e implementado separadamente, respeitando bloqueios do Android e dos aplicativos de origem.
