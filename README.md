# trivial-openai

A minmal API interface to the OpenAI API.

## Usage

`[trivial-openai "0.0.0"]`

### Basics

In your environment, set `OPENAI_API_KEY` to the value of your API key. You can get one [from here](https://platform.openai.com/account/api-keys).

In your code

```
(ns ...
    (:require [trivial-warning.core :as ai]))
...

(ai/models)
```

### External Functions

- `models`; returns a list of available models
- `completion`; takes a prompt and a bunch of optional parameters and returns the completion
- `chat`; takes a list of messages and a bunch of optional parameters and returns a chat response
- `transcription`; takes an audio filename and a bunch of optional parameters and returns the transcribed text
- `translation`; takes an audio filename and a bunch of optional paraeters and returns the _translated_ text
- `image`; takes a prompt and returns an image matching it
- `image-edit`; takes an image filename and a prompt and edits the image in some way (also, include a mask if you don't want to just get a completely different image)
- `image-variations`; takes an image and returns variations on it
- `image-url->file`; takes a url and a pathnamae. Downloads the image at the given URL to the given local path.

## License

Copyright © 2023 inaimathi<leo.zovic@gmail.com>

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
