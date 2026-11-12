# Pesquisa técnica: MVP ScreenTranslator

**Status**: A fazer antes de fechar o plano de implementação.

## Perguntas de pesquisa

1. Qual combinação de APIs Android permite captura de tela, serviço em primeiro plano e sobreposição no Android 11+ e versões posteriores?
2. Quais fontes permitem captura de áudio de reprodução e que comportamento é exigido quando ela é bloqueada?
3. Quais opções de OCR, tradução, reconhecimento de fala e síntese de fala oferecem inglês/espanhol → PT-BR, processamento local, latência e política de retenção adequados?
4. Qual é o custo medido de 1, 10 e 60 minutos, considerando OCR, caracteres traduzidos, áudio, voz sintetizada e tráfego?
5. Que limiares de mudança de tela e agrupamento de texto reduzem repetições sem perder conteúdo útil?
6. Quais requisitos de consentimento, divulgação e permissões se aplicam à distribuição pretendida?

## Evidências atuais dos materiais do projeto

- O documento técnico escolhe Android como primeira plataforma, sobreposição para texto, áudio e legendas quando possíveis, idioma de destino PT-BR e conjunto inicial limitado de idiomas.
- Decisões registradas incluem arquitetura híbrida automática, modos Econômico/Equilibrado/Qualidade, continuidade com funções locais sem internet, histórico temporário e salvamento manual.
- Os mesmos materiais reconhecem que conteúdo protegido e políticas de captura limitam a promessa de funcionar em qualquer aplicativo.
- A validação de proposta de valor com três italianos aponta para conversas presenciais por voz como necessidade mais evidente. Isso não valida a demanda de tradução sobre outros apps e exige confirmação de escopo.

## Decisões pendentes

- Provedores e processamento local versus nuvem.
- Política de descarte dos provedores remotos e controles de chave/credenciais.
- Comportamento final para áudio: somente legenda, voz sintetizada ou ambos.
- Se a tradução por microfone em conversa presencial é parte deste produto ou produto diferente.
- Métrica, aparelho de referência, conjunto de cenas e método para qualidade, atraso e bateria.

## Método

Antes de implementar a integração, registrar links para documentação oficial, matriz de compatibilidade por versão/aplicativo, resultados de prova técnica em aparelho, fórmula de custo e limitações observadas. Não assumir que uma descrição exploratória nos documentos substitui essa verificação.
