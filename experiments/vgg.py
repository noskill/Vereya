from torch import nn

class VGG(nn.Module):
   
    def __init__(self):
        super().__init__()
        empty = lambda x: x
        self.conv1a = empty
        self.conv1b = empty
        self.conv2a = empty
        self.conv2b = empty
        self.conv3a = empty
        self.conv3b = empty
        self.conv4a = empty
        self.conv4b = empty
        self.batchnorm0 = empty
        self.batchnorm1 = empty
        self.batchnorm2 = empty
        self.batchnorm3 = empty
        self.batchnorm4 = empty
        self.batchnorm5 = empty
        self.batchnorm6 = empty
        self.batchnorm7 = empty
        self.empty = empty

    def superblock(self, x, conv1, conv2, batch1, batch2):
        x = conv1(x)
        x = self.activation(x)
        x = batch1(x)
        x = conv2(x)
        x = self.activation(x)
        x = batch2(x)
        return x

    def vgg(self, x):
        x = self.superblock(x, self.conv1a, self.conv1b, self.batchnorm0, self.batchnorm1)
        x = self.pool(x)
        if self.conv2a is self.empty:
            return x
        x = self.superblock(x, self.conv2a, self.conv2b, self.batchnorm2, self.batchnorm3)
        x = self.pool(x)
        if self.conv3a is self.empty:
            return x
        x = self.superblock(x, self.conv3a, self.conv3b, self.batchnorm4, self.batchnorm5)
        x = self.pool(x)
        if self.conv4a is self.empty:
            return x
        x = self.superblock(x, self.conv4a, self.conv4b, self.batchnorm6, self.batchnorm7)
        return x
