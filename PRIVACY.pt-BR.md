# Política de privacidade

O Pixel Speed não coleta dados.

## O que o app lê

O app lê os contadores de tráfego de rede do próprio telefone pela interface
`TrafficStats` do Android, calcula uma velocidade a partir deles e a desenha na
tela. Os contadores nunca saem do telefone.

Com a troca de lado ativada, um serviço de acessibilidade lê a janela da barra
de status: quais ícones ela mostra e onde eles ficam. Ele não lê nenhuma outra
janela nem conteúdo. Isso também fica no telefone.

O Android também avisa o serviço quando as janelas na tela mudam, inclusive as
de outros apps. Esse sinal só indica quando olhar de novo para a barra de
status. De qualquer janela no topo da tela ele lê apenas a qual app ela
pertence, para distinguir a barra de status. Ele não lê conteúdo de nenhuma
janela além da barra de status.

O app não declara nenhuma permissão de rede, então não pode enviar nada para
lugar nenhum. Ele não contém análise de uso, serviço de relatório de falhas,
publicidade nem SDK de dados de terceiros.

## O que o app guarda

Suas configurações (perfis, posição, aparência, comportamento, estado de pausa)
ficam em um arquivo no armazenamento privado do app no telefone. O backup do
Android pode incluir esse arquivo conforme as configurações de backup do seu
dispositivo. Nada nele identifica você.

Exportar grava suas configurações em um arquivo que você escolhe. Importar lê um
arquivo que você escolhe. Os dois só acontecem quando você escolhe.

Se o app travar, ele guarda um registro dessa falha no seu armazenamento
privado: a hora, a versão do app, o stack trace e as últimas linhas de registro
do próprio app. Ajuda > Relatar um problema cria um arquivo de diagnóstico
quando você pede: a versão do app, o modelo do telefone e a versão do Android, a
geometria da tela, quais permissões foram concedidas, o estado da leitura, a
exportação das suas configurações, as linhas de registro recentes do app e esse
registro de falha, se houver. Ele não contém conta, id de dispositivo, número de
série nem localização, e nada vindo de outros apps. O app nunca o envia: você o
lê e depois o compartilha por conta própria, se quiser.

## Permissões

Cada permissão que o app declara e para que serve:

- Exibir sobre outros apps (SYSTEM_ALERT_WINDOW): desenha a leitura.
- Notificações (POST_NOTIFICATIONS, POST_PROMOTED_NOTIFICATIONS): a notificação
  do serviço em primeiro plano com pausar e retomar, e sua forma Live Update no
  Android 16 e posteriores.
- Serviço em primeiro plano (FOREGROUND_SERVICE,
  FOREGROUND_SERVICE_SPECIAL_USE): mantém a leitura funcionando enquanto a
  sobreposição está ativada.
- Serviço de acessibilidade (BIND_ACCESSIBILITY_SERVICE): opcional, desativado
  por padrão. Lê os ícones da barra de status e a posição deles para que a
  leitura possa ir para o lado da câmera com espaço. Nada mais.
- Estado da rede (ACCESS_NETWORK_STATE): informa se o telefone está online e
  qual conexão está ativa, para que a leitura mostre traços offline e conte a
  interface certa. Não é acesso à internet.
- Executar na inicialização (RECEIVE_BOOT_COMPLETED): inicia a sobreposição após
  uma reinicialização quando você ativa isso.
- Ignorar otimizações de bateria (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS):
  oferecido em telefones cujo fabricante fecha apps em segundo plano, para que a
  sobreposição continue. Opcional.

Não há permissão de rede.

## Contato

Dúvidas sobre esta política: abra um issue em
https://github.com/zachthedev/pixel-speed/issues.

Esta política vale para o Pixel Speed a partir da versão 1.0.0. Mudanças são
publicadas neste arquivo.
